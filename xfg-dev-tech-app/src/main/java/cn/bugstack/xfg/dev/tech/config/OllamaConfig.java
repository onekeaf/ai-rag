package cn.bugstack.xfg.dev.tech.config;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author oneleaf
 * @description
 * @create 下午9:01
 */
@Configuration
public class OllamaConfig {

    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl){
        return new OllamaApi(baseUrl);
    }

    @Bean
    public OllamaChatClient ollamaChatclient(OllamaApi ollamaApi){
        return new OllamaChatClient(ollamaApi);
    }

}
