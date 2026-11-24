package gravit.admin.qa.checklist.repository;

import gravit.admin.qa.checklist.domain.CheckCellHistory;
import gravit.admin.qa.feature.domain.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CheckCellHistoryRepository extends JpaRepository<CheckCellHistory, Long> {

    List<CheckCellHistory> findAllByItemIdAndPartOrderByChangedAtDescIdDesc(
            long itemId,
            Part part
    );

    List<CheckCellHistory> findAllByItemIdInAndPartOrderByChangedAtDescIdDesc(
            Collection<Long> itemIds,
            Part part
    );
}
