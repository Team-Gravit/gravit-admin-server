package gravit.admin.adapter.github.dto.internal;

public record GithubTreeEntryDto(
        String path,
        String blobSha,
        long size
) {
}
