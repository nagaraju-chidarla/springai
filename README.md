The application loads an HR policy document, splits it into smaller chunks, generates vector embeddings, stores those embeddings in PostgreSQL using pgvector, and uses similarity search to retrieve relevant policy information before asking Llama to generate the final answer.


---


# 1. Architecture


```text
                    User
                      |
                      | Question
                      v
              +---------------+
              |  ChatService   |
              +---------------+
                      |
                      v
              +---------------+
              |   RagService   |
              +---------------+
                      |
                      | similaritySearch()
                      v
              +-------------------+
              | PostgreSQL        |
              | + pgvector        |
              |                   |
              | Document          |
              | Embedding         |
              | Metadata          |
              +-------------------+
                      |
                      | Top K relevant
                      | documents
                      v
              +---------------+
              |  RagService    |
              | buildContext()  |
              +---------------+
                      |
                      | Context + Question
                      v
              +---------------+
              |   ChatClient   |
              +---------------+
                      |
                      v
                  Ollama
                      |
                      v
                 Llama 3.2
                      |
                      v
                Final Answer
2. What is RAG?

RAG stands for:

Retrieval-Augmented Generation

Instead of directly asking the LLM:

Question -> LLM -> Answer

we first retrieve relevant information from our own database:

Question
   |
   v
Vector Search
   |
   v
Relevant HR Policy
   |
   v
LLM
   |
   v
Answer

This allows the application to answer questions using company-specific information.

For example:

Question:
How many days can employees work from home?


        |
        v


PostgreSQL / pgvector


        |
        v


Relevant document:
Work From Home:
Employees can work from home up to 3 days per week.


        |
        v


Llama


        |
        v


Answer:
Employees can work from home up to 3 days per week.
3. Project Components
Spring Boot

The main application framework.

Version:

Spring Boot 3.5.6
Spring AI

Used to integrate:

Ollama
ChatClient
VectorStore
Documents
Embeddings
RAG functionality

Version:

Spring AI 1.0.1
Ollama

Ollama runs the LLM locally.

Current model:

llama3.2

Ollama is responsible for generating the final response.

It can also be used for generating embeddings if an embedding model is configured.

PostgreSQL

PostgreSQL stores the RAG data.

Database:

hrdb

User:

postgres
pgvector

pgvector is a PostgreSQL extension that allows PostgreSQL to store and search vector embeddings.

Instead of storing only:

Employee can work from home up to 3 days per week.

we also store a vector representation of that text.

Example:

[-0.0014, 0.0511, -0.1485, ...]

This vector represents the semantic meaning of the document.

4. Docker PostgreSQL Setup

We use the pgvector Docker image:

pgvector/pgvector:pg17

Example Docker Compose:

services:


  pgvector:
    image: pgvector/pgvector:pg17
    container_name: pgvector
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: hrdb
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data


volumes:
  pgdata:

Start PostgreSQL:

docker compose up -d

Check the container:

docker ps

Expected:

pgvector/pgvector:pg17

PostgreSQL will be available at:

Host: localhost
Port: 5432
Database: hrdb
Username: postgres
Password: postgres
5. Why Port 5432 Is Exposed

The Spring Boot application and DB viewer are running outside the Docker container.

Therefore PostgreSQL needs to be accessible from the host machine.

Windows Host
     |
     | localhost:5432
     |
     v
Docker
     |
     v
PostgreSQL

The Docker configuration:

ports:
  - "5432:5432"

means:

Host Port 5432 -> Container Port 5432

This allows:

Spring Boot application
DB Viewer
pgAdmin
IntelliJ database tools

to connect to PostgreSQL using:

localhost:5432

The port does NOT need to be exposed publicly to the internet.

6. Install Ollama Using Docker

Ollama can also run inside Docker.

Pull the Ollama image:

docker pull ollama/ollama

Run the container:

docker run -d \
  --name ollama \
  -p 11434:11434 \
  -v ollama:/root/.ollama \
  ollama/ollama

On Windows PowerShell, use:

docker run -d `
  --name ollama `
  -p 11434:11434 `
  -v ollama:/root/.ollama `
  ollama/ollama

Check:

docker ps

Ollama should be running on:

http://localhost:11434
7. Install Llama 3.2

After starting Ollama:

docker exec -it ollama ollama pull llama3.2

Check installed models:

docker exec -it ollama ollama list

You should see something similar to:

NAME            SIZE
llama3.2        ...

Test the model:

docker exec -it ollama ollama run llama3.2

Then ask:

Hello

To exit:

/bye
8. Embedding Model

RAG requires embeddings.

The embedding model converts text into vectors.

For example:

"Employees can work from home 3 days per week."

becomes something like:

[-0.0014, 0.0511, -0.1485, ...]

The vector is stored in PostgreSQL.

A common Ollama embedding model is:

nomic-embed-text

Pull it:

docker exec -it ollama ollama pull nomic-embed-text

Check:

docker exec -it ollama ollama list

You should have:

llama3.2
nomic-embed-text
9. PostgreSQL Configuration

The Spring Boot application needs to connect to PostgreSQL.

Example:

spring.datasource.url=jdbc:postgresql://localhost:5432/hrdb
spring.datasource.username=postgres
spring.datasource.password=postgres

For Ollama:

spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text

The exact property names should match the Spring AI version used by the project.

10. pgvector Extension

PostgreSQL normally does not understand vector data.

The pgvector extension adds vector support.

Connect to:

hrdb

and execute:

CREATE EXTENSION IF NOT EXISTS vector;

Verify:

SELECT * 
FROM pg_extension
WHERE extname = 'vector';

Expected:

vector
11. Database Structure

Spring AI's PGVector implementation stores documents and their embeddings in PostgreSQL.

Conceptually, the table contains:

+--------------------------------------+
| vector_store                         |
+--------------------------------------+
| id                                   |
| content                              |
| metadata                             |
| embedding                            |
+--------------------------------------+

For example:

id:
9ec30b37-691f-4cba-a7ed-ef2bb497637e


content:
NIT Solutions HR Policy


metadata:
{
  "source": "hr-policy.txt",
  "charset": "UTF-8",
  "section": "NIT Solutions HR Policy",
  "documentType": "HR_POLICY"
}


embedding:
[-0.0014, 0.0511, -0.1485, ...]

The exact table name depends on the Spring AI PGVector configuration/version.

12. Document Loading

The HR document is located at:

src/main/resources/docs/hr-policy.txt

The DocumentLoader class loads this document.

@Value("classpath:docs/hr-policy.txt")
private Resource resource;

The document is read using:

TextReader reader = new TextReader(resource);


List<Document> documents = reader.get();

At this point we have the original document.

13. Document Splitting

Large documents should not be inserted as one huge document.

The application uses:

HrPolicySplitter splitter = new HrPolicySplitter();


List<Document> chunks = splitter.apply(documents);

For example:

HR Policy
    |
    +-- Work From Home
    |
    +-- Leave Policy
    |
    +-- Working Hours
    |
    +-- Employee Benefits

Each section becomes a separate Document chunk.

This is important because RAG searches for the most relevant chunks.

14. Metadata

Before inserting the chunks into PostgreSQL, metadata is added:

chunk.getMetadata().put("documentType", "HR_POLICY");


chunk.getMetadata().put(
    "section",
    section
);


chunk.getMetadata().put(
    "source",
    resource.getFilename()
);

For example:

{
  "documentType": "HR_POLICY",
  "section": "Work From Home:",
  "source": "hr-policy.txt"
}

This metadata is stored along with the document.

15. Inserting Data Into PostgreSQL

This is the most important line in DocumentLoader:

vectorStore.add(chunks);

When this executes, Spring AI performs the vector-store insertion.

Conceptually:

Document chunk
      |
      v
Embedding Model
      |
      v
Vector
      |
      v
PostgreSQL + pgvector

So the application does NOT simply insert the text.

It stores:

Document text
+
Metadata
+
Embedding vector
16. Why Data Was Being Duplicated

DocumentLoader contains:

@PostConstruct
public void loadDocument()

@PostConstruct runs automatically when Spring Boot starts.

Therefore:

Application starts
       |
       v
DocumentLoader created
       |
       v
@PostConstruct executes
       |
       v
Read hr-policy.txt
       |
       v
Split document
       |
       v
Generate embeddings
       |
       v
vectorStore.add(chunks)
       |
       v
Insert into PostgreSQL

Every time the application restarts:

vectorStore.add(chunks)

runs again.

Therefore the same document can be inserted again.

This is why you saw duplicate records.

17. Important Production Improvement

The document loader should not blindly insert the same document every time.

Possible approaches:

Delete existing documents before inserting.
Check whether the document already exists.
Store a unique document ID/version.
Use a database uniqueness strategy.
Run ingestion separately instead of during application startup.

For a learning project, a simple approach is to delete the previous HR policy vectors before inserting the latest version.

Later, the ingestion process can be made more sophisticated.

18. RAG Search Flow

When the user asks:

How many days can employees work from home?

the request goes to:

ChatService.askLlama(question)

First:

List<Document> documents =
        ragService.search(question);
19. Similarity Search

Inside RagService:

SearchRequest searchRequest = SearchRequest.builder()
        .query(question)
        .topK(3)
        .similarityThreshold(0.75)
        .build();

The important parameters are:

topK = 3

Return at most 3 relevant documents.

And:

similarityThreshold = 0.75

Only return documents whose similarity is above the configured threshold.

Conceptually:

User Question
      |
      v
Embedding Model
      |
      v
Question Vector
      |
      v
pgvector similarity search
      |
      +---- Work From Home      0.91
      |
      +---- Leave Policy        0.42
      |
      +---- Benefits            0.31
      |
      v
Relevant Documents

With a threshold of 0.75, only sufficiently similar documents are returned.

20. Building the Context

After retrieving the documents:

String context = ragService.buildContext(documents);

The application combines the retrieved documents into a context:

Source: hr-policy.txt
Section: Work From Home:


Policy:
Employees can work from home up to 3 days per week.

This context is then given to the LLM.

21. Sending Context to Llama

ChatService uses:

chatClient.prompt()

The system instruction tells the model:

Answer the user's question using ONLY the
information provided in the company policy.

The user message contains:

Company Policy:


-------------------------
<retrieved documents>
-------------------------


Employee Question:


<question>

Therefore Llama does not need to know the entire HR policy.

It receives only the relevant retrieved information.

22. Final RAG Flow

The complete flow is:

                 USER
                   |
                   | Question
                   v
             ChatService
                   |
                   v
              RagService
                   |
                   | similaritySearch()
                   v
             Embedding Model
                   |
                   v
              Question Vector
                   |
                   v
          PostgreSQL + pgvector
                   |
                   | Similarity Search
                   v
           Top 3 Relevant Chunks
                   |
                   v
          buildContext()
                   |
                   v
          ChatClient / Ollama
                   |
                   v
              Llama 3.2
                   |
                   v
              Final Answer
23. Document Ingestion Flow

The document insertion flow is separate:

Application Startup
        |
        v
DocumentLoader
        |
        v
hr-policy.txt
        |
        v
TextReader
        |
        v
HrPolicySplitter
        |
        v
Document Chunks
        |
        v
Add Metadata
        |
        v
Embedding Model
        |
        v
Vector Embeddings
        |
        v
VectorStore.add()
        |
        v
PostgreSQL
        |
        v
pgvector
24. Query Flow

The query flow is:

User Question
      |
      v
ChatService
      |
      v
RagService.search()
      |
      v
Generate Question Embedding
      |
      v
pgvector similarity search
      |
      v
Relevant Document Chunks
      |
      v
buildContext()
      |
      v
ChatClient
      |
      v
Ollama
      |
      v
Llama 3.2
      |
      v
Answer
25. Why PostgreSQL Is Needed

Without RAG:

Question -> Llama -> Answer

The LLM only knows what was included in its prompt and what it learned during training.

With RAG:

Question
   |
   v
Company Database
   |
   v
Relevant Company Information
   |
   v
Llama
   |
   v
Answer

This allows us to keep company-specific information outside the model.

For example:

HR Policy
Employee Handbook
IT Policies
Leave Policies
Benefits
Security Policies

can all be stored in PostgreSQL and retrieved when needed.

26. DB Viewer

Connect DB Viewer using:

Host: localhost
Port: 5432
Database: hrdb
Username: postgres
Password: postgres

You can inspect:

Tables
Extensions
Documents
Metadata
Embeddings
Vector store records

To verify pgvector:

SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
27. Useful PostgreSQL Commands

Connect to PostgreSQL:

docker exec -it pgvector psql -U postgres -d hrdb

List databases:

\l

List tables:

\dt

List extensions:

\dx

Check pgvector:

SELECT * 
FROM pg_extension
WHERE extname = 'vector';

Exit:

\q
28. Useful Docker Commands

Check running containers:

docker ps

Start PostgreSQL:

docker start pgvector

Stop PostgreSQL:

docker stop pgvector

View PostgreSQL logs:

docker logs pgvector

Start Ollama:

docker start ollama

Stop Ollama:

docker stop ollama

View Ollama logs:

docker logs ollama
29. Project Structure

Recommended project structure:

springai
│
├── src
│   └── main
│       ├── java
│       │   └── com.ai.springai
│       │       │
│       │       ├── config
│       │       │   └── HrPolicySplitter.java
│       │       │
│       │       ├── rag
│       │       │   └── DocumentLoader.java
│       │       │
│       │       └── service
│       │           ├── RagService.java
│       │           └── ChatService.java
│       │
│       └── resources
│           ├── docs
│           │   └── hr-policy.txt
│           │
│           └── application.properties
│
├── pom.xml
└── README.md
30. Running the Project
Step 1 - Start PostgreSQL
docker compose up -d

Verify:

docker ps
Step 2 - Start Ollama

If using Docker:

docker start ollama

Verify:

docker ps
Step 3 - Verify models
docker exec -it ollama ollama list

Required models:

llama3.2
nomic-embed-text
Step 4 - Verify PostgreSQL

Connect using DB Viewer:

Host: localhost
Port: 5432
Database: hrdb
Username: postgres
Password: postgres
Step 5 - Start Spring Boot

Using Maven:

mvn spring-boot:run

Or run the main Spring Boot application from IntelliJ IDEA.

31. Example Question

Ask:

How many days can employees work from home?

Expected answer:

According to the company policy, employees can work from home
up to 3 days per week.


Source: hr-policy.txt
Section: Work From Home:
32. Debugging RAG

RagService prints useful information:

======================================
Question: How many days can employees
work from home?


Documents found: 1


--------------------------------------
Score: 0.91
Source: hr-policy.txt
Content:
Employees can work from home up to 3 days per week.
======================================

This helps determine whether the problem is:

Question
   |
   v
Embedding
   |
   v
Vector Search
   |
   v
No documents?
   |
   +---- YES -> Check embedding/search configuration
   |
   +---- NO
        |
        v
      Llama
        |
        v
      Answer
33. Important Concepts
Document

The original source information.

Example:

hr-policy.txt
Chunk

A smaller section of the document used for retrieval.

Example:

Work From Home:
Employees can work from home up to 3 days per week.
Embedding

Numerical representation of the meaning of text.

Example:

[-0.0014, 0.0511, -0.1485, ...]
Vector Store

Storage system for embeddings.

Here:

PostgreSQL + pgvector
Similarity Search

Finds documents whose meaning is closest to the question.

LLM

Generates the final natural-language answer.

Here:

Llama 3.2
RAG

Combines:

Retrieval + LLM Generation
