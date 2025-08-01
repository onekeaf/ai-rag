package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IRAGService;
import cn.bugstack.xfg.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * @author oneleaf
 * @description
 * @create 下午8:21
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;
    @Override
    @GetMapping("/query_rag_tag_list")
    public Response<List<String>> queryRagTagList() {
        // 从Redisson客户端获取名为"ragTag"的列表，用于存储和管理知识库标签
        RList<String> elements = redissonClient.getList("ragTag");
        // 构造并返回响应结果，包含查询到的知识库标签列表
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }

    @Override
    @PostMapping("/file/upload")
    public Response<String> uploadFile(@RequestParam String ragTag,@RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始{}",ragTag);
        for (MultipartFile file : files) {
            // 初始化TikaDocumentReader，用于读取和解析指定路径的文档文件
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            // 从文档读取器中获取原始文档内容
            List<Document> documents = reader.get();
            // 使用TokenTextSplitter对文档进行分块处理，将大文档拆分成适合模型处理的小片段
            List<Document> splitDocumentsList = tokenTextSplitter.apply(documents);

            // 为原始文档和分块后的文档添加元数据，标记所属的知识库名称
            documents.forEach(document -> document.getMetadata().put("knowledge", ragTag));
            splitDocumentsList.forEach(document -> document.getMetadata().put("knowledge", ragTag));

            // 将分块后的文档列表存入PgVectorStore向量数据库中，以便后续进行相似性搜索和检索
            pgVectorStore.accept(splitDocumentsList);


            // 从Redisson客户端获取名为"ragTag"的列表，用于存储和管理知识库标签
            RList<String> elements = redissonClient.getList("ragTag");
            // 如果该标签尚未存在于列表中，则将其添加到列表中，避免重复记录
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }

        }
        log.info("上传知识库完成{}",ragTag);
        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    @Override
    @PostMapping("/analyze_git_repository")
    public Response<String> analyzeGitRepo(@RequestParam String repoUrl,@RequestParam String userName,@RequestParam String token) throws Exception {

        String localPath = "./git-cloned-repo";
        String repoProjectName = extractProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        // 删除已存在的目录
        FileUtils.deleteDirectory(new File(localPath));

        // 创建Git对象，并设置克隆的URI、目录、用户名和密码
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .call();

        // 遍历目录下的所有文件
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());
                try {
                    // 初始化TikaDocumentReader，用于读取和解析指定路径的文档文件
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    // 为原始文档和分块后的文档添加元数据，标记所属的知识库名称
                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    // 将分块后的文档列表存入PgVectorStore向量数据库中，以便后续进行相似性搜索和检索
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    // 将分块后的文档列表存入PgVectorStore向量数据库中
                    pgVectorStore.accept(documentSplitterList);
                } catch (Exception e) {
                    log.error("遍历解析路径，上传知识库失败:{}", file.getFileName());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.info("Failed to access file: {} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        // 删除已克隆的目录
        FileUtils.deleteDirectory(new File(localPath));

        // 将知识库名称添加到Redisson客户端的"ragTag"列表中
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(repoProjectName)) {
            elements.add(repoProjectName);
        }

        git.close();

        log.info("遍历解析路径，上传完成:{}", repoUrl);

        return Response.<String>builder().code("0000").info("调用成功").build();
    }


    // 截取git地址中的知识库名称
    private String extractProjectName(String repoUrl) {
        // 将仓库URL按照"/"分割成多个部分
        String[] parts = repoUrl.split("/");
        // 取出最后一部分（即仓库名称），并移除.git后缀，返回知识库名称
        return parts[parts.length - 1].replace(".git", "");
    }
}
