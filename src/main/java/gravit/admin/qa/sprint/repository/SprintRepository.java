package gravit.admin.qa.sprint.repository;

import gravit.admin.qa.sprint.domain.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    boolean existsByNotionPageId(String notionPageId);

    boolean existsBySprintKey(String sprintKey);

    List<Sprint> findAllByOrderByCreatedAtDescIdDesc();
}
