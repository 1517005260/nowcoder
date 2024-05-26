package com.nowcoder.community.service;

import com.nowcoder.community.dao.elasticsearch.DiscussPostRepository;
import com.nowcoder.community.dao.elasticsearch.UserRepository;
import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.CriteriaQueryBuilder;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ElasticsearchService {

    @Autowired
    private DiscussPostRepository discussRepository;  // dao

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ElasticsearchTemplate elasticTemplate;

    // 保存与修改（新的save相当于修改）
    public void saveDiscussPost(DiscussPost post){
        discussRepository.save(post);
    }

    public void saveUser(User user){userRepository.save(user);}

    // 删除
    public void deleteDiscussPost(int id){
        discussRepository.deleteById(id);
    }

    public void deleteUser(int id){userRepository.deleteById(id);}

    // 搜索，可以复用上次的test代码
    public Page<DiscussPost> searchDiscussPost(String keyword, int current, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 处理空关键字的情况，例如返回空结果
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(current, limit), 0);
        }

        // 使用 wildcard 查询进行模糊匹配
        String wildcardKeyword = "*" + keyword + "*";
        Criteria criteria = new Criteria("title").expression(wildcardKeyword)
                .or(new Criteria("content").expression(wildcardKeyword));

        // 这里的高亮格式还是<em></em>，但是前端的global.css已经处理为了高亮红色
        List<HighlightField> highlightFieldList = new ArrayList<>();
        HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        Highlight highlight = new Highlight(highlightFieldList);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

        CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
                .withSort(Sort.by(Sort.Direction.DESC, "type"))
                .withSort(Sort.by(Sort.Direction.DESC, "score"))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withHighlightQuery(highlightQuery)
                .withPageable(PageRequest.of(current, limit));
        CriteriaQuery query = new CriteriaQuery(builder);

        SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);
        if (result.isEmpty()) {
            return null;
        }

        List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
        List<DiscussPost> discussPostList = new ArrayList<>();
        for (SearchHit<DiscussPost> hit : searchHitList) {
            DiscussPost post = hit.getContent();
            var titleHighlight = hit.getHighlightField("title");
            if (titleHighlight.size() != 0) {
                String originalTitle = post.getTitle();
                String highlightedTitle = highlightMatch(originalTitle, keyword);
                post.setTitle(highlightedTitle);
            }
            var contentHighlight = hit.getHighlightField("content");
            if (contentHighlight.size() != 0) {
                String originalContent = post.getContent();
                String highlightedContent = highlightMatch(originalContent, keyword);
                post.setContent(highlightedContent);
            }
            discussPostList.add(post);
        }

        return new PageImpl<>(discussPostList, PageRequest.of(current, limit), result.getTotalHits());
    }

    public Page<User> searchUser(String keyword, int current, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 处理空关键字的情况，例如返回空结果
            return new PageImpl<>(new ArrayList<>(), PageRequest.of(current, limit), 0);
        }

        String wildcardKeyword = "*" + keyword + "*";
        Criteria criteria = new Criteria("username").expression(wildcardKeyword);

        List<HighlightField> highlightFieldList = new ArrayList<>();
        HighlightField highlightField = new HighlightField("username", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
        highlightFieldList.add(highlightField);
        Highlight highlight = new Highlight(highlightFieldList);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, User.class);

        CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)
                .withSort(Sort.by(Sort.Direction.DESC, "type"))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withHighlightQuery(highlightQuery)
                .withPageable(PageRequest.of(current, limit));
        CriteriaQuery query = new CriteriaQuery(builder);

        SearchHits<User> result = elasticTemplate.search(query, User.class);
        if (result.isEmpty()) {
            return null;
        }

        List<SearchHit<User>> searchHitList = result.getSearchHits();
        List<User> UserList = new ArrayList<>();
        for (SearchHit<User> hit : searchHitList) {
            User user = hit.getContent();
            var usernameHighlight = hit.getHighlightField("username");
            if (usernameHighlight.size() != 0) {
                // 手动高亮匹配部分
                String originalUsername = user.getUsername();
                String highlightedUsername = highlightMatch(originalUsername, keyword);
                user.setUsername(highlightedUsername);
            }
            UserList.add(user);
        }

        return new PageImpl<>(UserList, PageRequest.of(current, limit), result.getTotalHits());
    }

    private String highlightMatch(String text, String keyword) {
        // 使用正则表达式手动高亮匹配部分
        String patternString = "(?i)(" + Pattern.quote(keyword) + ")";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(text);
        StringBuffer highlightedText = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(highlightedText, "<em>" + matcher.group(1) + "</em>");
        }
        matcher.appendTail(highlightedText);

        return highlightedText.toString();
    }

}
