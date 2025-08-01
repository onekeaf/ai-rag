package cn.bugstack.xfg.dev.tech.trigger.http;

import cn.bugstack.xfg.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


/**
 * @author oneleaf
 * @description
 * @create 下午8:43
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient ollamaClient;


    /**
     * http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=hi
     */
    @Override
    @GetMapping("/generate")
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return ollamaClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
     */
    @Override
    @GetMapping("/generate_stream")
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return ollamaClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }
}
