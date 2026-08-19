package com.ai.springai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private static final String NO_INFORMATION =
            "I don't have that information in the company policy.";

    private final ChatClient chatClient;
    private final RagService ragService;

    public ChatService(ChatClient.Builder builder,
                       RagService ragService) {

        this.chatClient = builder.build();
        this.ragService = ragService;
    }

    public String askLlama(String question) {

        // 1. Search PGVector
        List<Document> documents = ragService.search(question);

        if (documents.isEmpty()) {
            return NO_INFORMATION;
        }

        // 2. Build context from retrieved documents
        String context = ragService.buildContext(documents);

        // 3. Ask Ollama
        String answer = chatClient.prompt()
                .system("""
                        You are the HR Assistant for NIT Solutions Company.

                        Answer the employee's question using ONLY the
                        information provided in the company policy.

                        Rules:
                        - Do not use your own knowledge.
                        - Do not make up company policies.
                        - Do not assume information that isn't present.
                        - If the answer cannot be found in the policy,
                          respond exactly with:

                          I don't have that information in the company policy.
                        """)
                .user("""
                        Company Policy Context:
                        
                        -------------------------
                        %s
                        -------------------------

                        Employee Question:
                        %s
                        """.formatted(context, question))
                .call()
                .content();

        // 4. Add source from the actual retrieved documents
        String sources = documents.stream()
                .map(document -> {
                    String source = String.valueOf(
                            document.getMetadata().get("source")
                    );

                    String section = String.valueOf(
                            document.getMetadata().get("section")
                    );

                    return "Source: " + source +
                            "\nSection: " + section;
                })
                .distinct()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        return answer + "\n\n" + sources;
    }
}