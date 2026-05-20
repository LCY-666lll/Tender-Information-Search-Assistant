package com.xfusion.bidaggregator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xfusion.bidaggregator.config.AppProperties;
import com.xfusion.bidaggregator.model.BidItem;
import com.xfusion.bidaggregator.model.SearchIntent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchServiceTest {
    private final WebSearchService service = new WebSearchService(
            new AppProperties(), new InvalidPageFilter(), new ValidAnnouncementScorer());

    @Test
    void resolvesBingRedirectToOriginalUrl() {
        String target = "https://www.ccgp.gov.cn/cggg/zygg/gkzb/202605/t20260506_26514614.htm";
        String encoded = "a1" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(target.getBytes(StandardCharsets.UTF_8));
        String bing = "https://www.bing.com/ck/a?!&&p=x&u="
                + URLEncoder.encode(encoded, StandardCharsets.UTF_8)
                + "&ntb=1";

        assertThat(service.resolveSearchUrl(bing)).isEqualTo(target);
    }

    @Test
    void parsesConcreteSogouTenderSnippetForBeijingChipSearch() {
        SearchIntent intent = new SearchIntent();
        intent.setProvince("北京");
        intent.setCity("北京");
        intent.setKeyword("芯片");
        String html = """
                <div class="vrwrap">
                  <h3 class="vr-title"><a href="https://www.qianlima.com/gjxx/149824/index_2_0.html">
                    北京集成电路半导体招标公告
                  </a></h3>
                  <div class="fz-mid">【招标公告】集成电路学院功率放大器等芯片 MPW 加工流片及芯片封装服务比价采购项目采购公告，北京市，2026-04-30，项目包含采购范围、投标要求和开标安排。</div>
                </div><!--STATUS VR OK-->
                """;

        List<BidItem> items = service.parseSogouBlocks(html, intent, "2026 北京 芯片 招标公告", 5);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSourceUrl()).contains("qianlima.com/gjxx/149824");
        assertThat(items.get(0).getPublishTime()).isNotNull();
        assertThat(items.get(0).getCoreContent()).contains("芯片", "招标公告", "北京市");
    }
}
