package cn.bugstack.xfg.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.awt.desktop.SystemEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author oneleaf
 * @description
 * @create 下午4:32
 */

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        // 初始化TikaDocumentReader，用于读取和解析指定路径的文档文件
        // Tika是一个内容分析工具包，可以自动检测和解析多种文档格式（如PDF、DOC、TXT等）
        // 此处读取的是项目资源目录下的"data/file.text"文件
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.text");

        // 从文档读取器中获取原始文档内容
        List<Document> documents = reader.get();
        // 使用TokenTextSplitter对文档进行分块处理，将大文档拆分成适合模型处理的小片段
        List<Document> splitDocumentsList = tokenTextSplitter.apply(documents);

        // 为原始文档和分块后的文档添加元数据，标记所属的知识库名称
        documents.forEach(document -> document.getMetadata().put("knowledge", "知识库名称"));
        splitDocumentsList.forEach(document -> document.getMetadata().put("knowledge", "知识库名称"));

        // 将分块后的文档列表添加到 PostgreSQL 向量存储中
        // 这一步会将文档片段及其向量表示存储到数据库中，以便后续进行相似性搜索
        pgVectorStore.add(splitDocumentsList);

        log.info("文档上传并处理成功，共处理 {} 个文档片段", splitDocumentsList.size());
    }

    @Test
    public void test() {
        String message = "维克托·伊万诺夫书房挂的航海图与盗窃动机有何潜在联系？";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 创建一个搜索请求，使用用户输入的消息作为查询条件
        // withTopK(5) 表示返回最相似的5个文档
        // withFilterExpression("knowledge = '知识库名称'") 表示只在指定的知识库中进行搜索
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '知识库名称'");

        // 执行相似性搜索，从 PostgreSQL 向量存储中检索与用户查询最相关的文档片段
        List<Document> documents = pgVectorStore.similaritySearch(request);
        // 将检索到的文档内容进行拼接，形成一个完整的上下文字符串，用于后续的问答生成
        String documentsCollector = documents.stream()
                .map(Document::getContent)  // 提取每个文档的内容
                .collect(Collectors.joining("\n"));  // 使用换行符连接所有文档内容

        // 使用SystemPromptTemplate将文档内容插入到系统提示模板中，创建一个包含相关文档信息的消息对象
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage
                (Map.of("documents", documentsCollector));// 将documentsCollector作为模板变量传入SYSTEM_PROMPT中的{documents}占位符

        // 创建一个消息列表，用于存储用户消息和系统RAG消息
        ArrayList<Message> messages = new ArrayList<>();
        // 添加用户原始查询消息
        messages.add(new UserMessage(message));
        // 添加包含相关文档内容的系统RAG消息
        messages.add(ragMessage);

        // 调用OllamaChatClient的call方法发送消息列表和指定模型的配置选项
        // 使用deepseek-r1:1.5b模型进行对话生成
        ChatResponse chatResponse = ollamaChatClient.call(
                new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b"))
        );

        log.info("RAG结果：{}", JSON.toJSONString(chatResponse));

    }

}
