package com.ai.springai.rag;

import com.ai.springai.config.HrPolicySplitter;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.List;

@Service
public class DocumentLoader {

    private final VectorStore vectorStore;

    @Value("classpath:docs/hr-policy.txt")
    private Resource resource;


    public DocumentLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void loadDocument() {
        TextReader reader = new TextReader(resource);

        List<Document> documents = reader.get();

        System.out.println("Original Documents : " + documents.size());

        HrPolicySplitter splitter = new HrPolicySplitter();
        List<Document> chunks =  splitter.apply(documents);

        System.out.println("Generated Chunks   : " + chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("--------------------------------------");
            System.out.println("CHUNK " + (i + 1));
            System.out.println(chunks.get(i).getText());
        }

        for (Document chunk: chunks) {
            String text = chunk.getText();
            String section = text.split("\n")[0].trim();

            chunk.getMetadata().put("documentType", "HR_POLICY");
            chunk.getMetadata().put("section", section);
            chunk.getMetadata().put("source", resource.getFilename());
        }

        vectorStore.add(chunks);

        System.out.println("Documents successfully indexed.");
    }
}
