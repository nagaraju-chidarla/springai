package com.ai.springai.controller;

import com.ai.springai.rag.DocumentLoader;
import com.ai.springai.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final DocumentLoader documentLoader;

    public ChatController(ChatService chatService, DocumentLoader documentLoader) {
        this.chatService = chatService;
        this.documentLoader = documentLoader;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {

        return chatService.askLlama(message);
    }

    @GetMapping("/load")
    public void loadDocument() {
        documentLoader.loadDocument();
    }
}
