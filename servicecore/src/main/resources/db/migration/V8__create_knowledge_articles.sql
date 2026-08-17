CREATE TABLE knowledge_articles (
    id UUID PRIMARY KEY,

    title VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    steps TEXT NOT NULL,

    status VARCHAR(30) NOT NULL,
    owner_id VARCHAR(255) NOT NULL,
    review_date DATE NOT NULL,

    version INTEGER NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_knowledge_articles_status
        CHECK (
            status IN (
                'DRAFT',
                'PUBLISHED',
                'ARCHIVED'
            )
        ),

    CONSTRAINT chk_knowledge_articles_version
        CHECK (
            version > 0
        ),

    CONSTRAINT chk_knowledge_articles_title
        CHECK (
            LENGTH(TRIM(title)) > 0
        ),

    CONSTRAINT chk_knowledge_articles_category
        CHECK (
            LENGTH(TRIM(category)) > 0
        ),

    CONSTRAINT chk_knowledge_articles_steps
        CHECK (
            LENGTH(TRIM(steps)) > 0
        ),

    CONSTRAINT chk_knowledge_articles_owner
        CHECK (
            LENGTH(TRIM(owner_id)) > 0
        )
);

CREATE TABLE knowledge_article_versions (
    id UUID PRIMARY KEY,

    article_id UUID NOT NULL,
    version_number INTEGER NOT NULL,

    title VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    steps TEXT NOT NULL,

    owner_id VARCHAR(255) NOT NULL,
    review_date DATE NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_knowledge_article_versions_article
        FOREIGN KEY (article_id)
        REFERENCES knowledge_articles(id),

    CONSTRAINT uq_knowledge_article_version
        UNIQUE (
            article_id,
            version_number
        ),

    CONSTRAINT chk_knowledge_article_version_number
        CHECK (
            version_number > 0
        )
);

CREATE INDEX idx_knowledge_articles_status
    ON knowledge_articles (status);

CREATE INDEX idx_knowledge_articles_category
    ON knowledge_articles (category);

CREATE INDEX idx_knowledge_articles_owner
    ON knowledge_articles (owner_id);

CREATE INDEX idx_knowledge_articles_review_date
    ON knowledge_articles (review_date);

CREATE INDEX idx_knowledge_article_versions_article
    ON knowledge_article_versions (
        article_id,
        version_number DESC
    );