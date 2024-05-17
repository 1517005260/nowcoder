package com.nowcoder.community.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.util.ContentType;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ElasticsearchConfig {

    private static final String IP = "127.0.0.1";
    private static final Integer PORT = 9200;

    @Bean(name = "esClient")
    public ElasticsearchClient getClient() {
        RestClient restClient = RestClient.builder(new HttpHost(IP, PORT))
                .setHttpClientConfigCallback(httpClientBuilder
                        ->httpClientBuilder.setDefaultHeaders(
                                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())))
                        .addInterceptorLast((HttpResponseInterceptor) (response, context)
                                -> response.addHeader("X-Elastic-Product", "Elasticsearch"))).build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(transport);
        return client;
    }
}
