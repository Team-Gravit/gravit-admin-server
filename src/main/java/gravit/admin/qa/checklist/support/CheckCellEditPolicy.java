package gravit.admin.qa.checklist.support;

import gravit.admin.qa.checklist.domain.CellState;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.qa.sprint.domain.SprintStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static gravit.admin.global.exception.domain.CustomErrorCode.CHECK_CELL_LOCKED;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CheckCellEditPolicy {

    public static boolean isEditable(
            SprintStatus sprintStatus,
            CellState state
    ) {
        return sprintStatus == SprintStatus.MANUAL_REVIEW && state != CellState.NOT_APPLICABLE;
    }

    public static void requireEditable(CellState state) {
        if (state == CellState.NOT_APPLICABLE) {
            throw new RestApiException(CHECK_CELL_LOCKED);
        }
    }
}
