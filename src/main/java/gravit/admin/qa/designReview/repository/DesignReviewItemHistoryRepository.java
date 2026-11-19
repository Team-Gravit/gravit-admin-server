package gravit.admin.qa.designReview.repository;

import gravit.admin.qa.designReview.domain.DesignReviewItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DesignReviewItemHistoryRepository extends JpaRepository<DesignReviewItemHistory, Long> {

    List<DesignReviewItemHistory> findAllByItemIdOrderByChangedAtDescIdDesc(long itemId);

    List<DesignReviewItemHistory> findAllByItemIdInOrderByChangedAtDescIdDesc(Collection<Long> itemIds);
}
