-- 출처: gravit-server e43a270 V21__add_staging_label.sql(staging_label)
-- 스테이징 하위 테이블(V4)의 label FK는 대시보드 조회와 관계없어 옮기지 않았다. 스테이징 테이블을 옮길 때(5단계) 함께 둔다.
CREATE TABLE staging_label
(
    id          bigint       NOT NULL,
    label       varchar(32)  NOT NULL UNIQUE,
    unit_id     bigint       NOT NULL,
    description varchar(255) NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'PENDING',
    created_at  timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT staging_label_pkey PRIMARY KEY (id),
    CONSTRAINT staging_label_status_check CHECK (status IN ('PENDING', 'COMPLETED'))
);
