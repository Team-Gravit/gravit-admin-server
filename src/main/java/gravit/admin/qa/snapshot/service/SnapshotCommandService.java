package gravit.admin.qa.snapshot.service;

import gravit.admin.qa.snapshot.domain.Snapshot;
import gravit.admin.qa.snapshot.dto.internal.SnapshotCreateDto;
import gravit.admin.qa.snapshot.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SnapshotCommandService {

    private final SnapshotRepository snapshotRepository;

    @Transactional
    public List<Long> saveAll(
            long sprintId,
            long jobId,
            List<SnapshotCreateDto> snapshots
    ) {
        return snapshotRepository.saveAll(snapshots.stream()
                        .map(snapshot -> Snapshot.create(sprintId, jobId, snapshot))
                        .toList())
                .stream()
                .map(Snapshot::getId)
                .toList();
    }
}
