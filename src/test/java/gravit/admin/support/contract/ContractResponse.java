package gravit.admin.support.contract;

public record ContractResponse(
        int status,
        String contentType,
        byte[] body
) {

    public static ContractResponse of(
            int status,
            String contentType,
            byte[] body
    ) {
        return new ContractResponse(status, contentType, body == null ? new byte[0] : body);
    }
}
