package com.xfusion.bidaggregator.crawler;

import com.xfusion.bidaggregator.model.CrawlResult;
import com.xfusion.bidaggregator.model.SearchIntent;

public interface SourceCrawler {
    String sourceName();

    String sourceType();

    boolean needLogin();

    CrawlResult crawl(SearchIntent intent);
}
