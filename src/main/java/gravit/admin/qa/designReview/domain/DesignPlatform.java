package gravit.admin.qa.designReview.domain;

import gravit.admin.adapter.figma.domain.FigmaPlatform;
import gravit.admin.qa.feature.domain.Part;
import gravit.admin.qa.feature.dto.internal.SpecRowDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DesignPlatform {

    WEB(Part.WEB, FigmaPlatform.WEB),
    APP(Part.APP, FigmaPlatform.AOS);

    private final Part part;
    private final FigmaPlatform figmaPlatform;

    public boolean appliesTo(SpecRowDto row) {
        return this == WEB ? row.appliesToWeb() : row.appliesToApp();
    }
}
