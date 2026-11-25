-- 픽스처 적재 순서 확인용(메인 마이그레이션 아님). V01 파일의 probe_parent를 참조하므로 이름 순서대로 실행돼야 성공한다.
CREATE TABLE probe_child
(
    id        BIGINT PRIMARY KEY,
    parent_id BIGINT NOT NULL REFERENCES probe_parent (id)
);
