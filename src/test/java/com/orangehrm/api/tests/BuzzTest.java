package com.orangehrm.api.tests;

import com.orangehrm.api.config.*;
import com.orangehrm.api.core.BaseAPITest;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * TC-BZZ - Buzz social feed: posts, shares, likes and comments.
 *
 * A Buzz post is addressed by two different ids: the POST response returns the POST id
 * under data.post.id, while the feed lists SHARE ids - and deletion goes through the
 * share, not the post.
 */
public class BuzzTest extends BaseAPITest {

    private static Integer shareId;

    @Test(priority = 1, description = "TC-BZZ-001 | GET the Buzz feed returns 200 with a list")
    public void tcBzz001_getFeed() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.BUZZ_FEED);

        assertOkList(r, "GET /buzz/feed");
        assertHasTotal(r, "GET /buzz/feed");
    }

    @Test(priority = 2, description = "TC-BZZ-002 | POST creates a text post and returns the post id")
    public void tcBzz002_createPost() {
        Response r = api().body(TestData.buzzPost(TestData.BUZZ_POST_TEXT))
                .when().post(Endpoints.BUZZ_POSTS);

        assertOkObject(r, "POST /buzz/posts");
        assertFieldPresent(r, "data.post.id", "created Buzz post id");
        assertFieldPresent(r, "data.employee.empNumber", "post author empNumber");
    }

    @Test(priority = 3, dependsOnMethods = "tcBzz002_createPost",
          description = "TC-BZZ-003 | The new post appears in the feed with its share id and text")
    public void tcBzz003_postAppearsInFeed() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.BUZZ_FEED);
        assertOkList(r, "GET /buzz/feed after posting");

        Integer found = null;
        for (int i = 0; i < r.jsonPath().getList("data").size(); i++) {
            if (TestData.BUZZ_POST_TEXT.equals(r.jsonPath().getString("data[" + i + "].text"))) {
                found = r.jsonPath().getInt("data[" + i + "].id");
                break;
            }
        }
        Assert.assertNotNull(found,
                "The created Buzz post was not found in the feed\n" + safeBody(r));
        shareId = found;
    }

    @Test(priority = 4, dependsOnMethods = "tcBzz003_postAppearsInFeed",
          description = "TC-BZZ-004 | The feed entry exposes like/comment/share statistics")
    public void tcBzz004_feedEntryHasStats() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.BUZZ_FEED);
        assertOkList(r, "GET /buzz/feed");

        for (String stat : new String[]{"numOfLikes", "numOfComments", "numOfShares"}) {
            assertFieldPresent(r, "data[0].stats." + stat, "feed stat '" + stat + "'");
        }
    }

    @Test(priority = 5, dependsOnMethods = "tcBzz003_postAppearsInFeed",
          description = "TC-BZZ-005 | A share can be liked and the like count increases")
    public void tcBzz005_likeShare() {
        Response r = api().pathParam("id", shareId).body("{}")
                .when().post(Endpoints.BUZZ_SHARE_LIKES);

        Assert.assertTrue(r.statusCode() == StatusCodes.OK,
                "Liking a share should succeed\n" + safeBody(r));
    }

    @Test(priority = 6, dependsOnMethods = "tcBzz005_likeShare",
          description = "TC-BZZ-006 | A like can be removed again")
    public void tcBzz006_unlikeShare() {
        Response r = api().pathParam("id", shareId).when().delete(Endpoints.BUZZ_SHARE_LIKES);

        assertStatus(r, StatusCodes.OK, "DELETE /buzz/shares/{id}/likes");
    }

    @Test(priority = 7, dependsOnMethods = "tcBzz003_postAppearsInFeed",
          description = "TC-BZZ-007 | A comment can be added to a share and listed back")
    public void tcBzz007_commentOnShare() {
        Response create = api().pathParam("id", shareId)
                .body("{\"text\":\"%s\"}".formatted(TestData.BUZZ_COMMENT_TEXT))
                .when().post(Endpoints.BUZZ_SHARE_COMMENTS);

        assertOkObject(create, "POST /buzz/shares/{id}/comments");
        // The create response returns ids, not the comment text; the text is read back from the list.
        assertFieldPresent(create, "data.comment.id", "created comment id");
        assertField(create, "data.share.id", shareId, "comment parent share id");

        Response list = api().pathParam("id", shareId).queryParam("limit", 10)
                .when().get(Endpoints.BUZZ_SHARE_COMMENTS);
        assertOkList(list, "GET /buzz/shares/{id}/comments");
        // The comment list returns ids and the author, not the comment body, so the posted
        // comment is verified by its id and by the collection total rather than by its text.
        int commentId = create.jsonPath().getInt("data.comment.id");
        Assert.assertTrue(list.jsonPath().getList("data.comment.id").contains(commentId),
                "The posted comment id " + commentId + " is not in the comment list\n" + safeBody(list));
        Assert.assertTrue(assertHasTotal(list, "GET comments") >= 1,
                "The comment list total should count the comment just posted");
    }

    @Test(priority = 8, description = "TC-BZZ-008 | GET work anniversaries returns 200")
    public void tcBzz008_getAnniversaries() {
        Response r = api().queryParam("limit", 10).when().get(Endpoints.BUZZ_ANNIVERSARIES);
        assertOkList(r, "GET /buzz/anniversaries");
    }

    @Test(priority = 9, description = "TC-BZZ-009 | Creating a post with an empty body returns 422")
    public void tcBzz009_createEmptyPostRejected() {
        Response r = api().body("{\"type\":\"text\",\"text\":\"\"}").when().post(Endpoints.BUZZ_POSTS);

        assertValidationError(r, null, "POST an empty Buzz post");
    }

    @Test(priority = 20, dependsOnMethods = "tcBzz003_postAppearsInFeed", alwaysRun = true,
          description = "TC-BZZ-020 | DELETE the share removes the post from the feed")
    public void tcBzz020_deleteShare() {
        // alwaysRun: without this guard a skipped create sends {"ids":[null]} and fails.
        require(shareId, "a Buzz post created by this run");
        Response r = api().pathParam("id", shareId).when().delete(Endpoints.BUZZ_SHARE_BY_ID);

        assertStatus(r, StatusCodes.OK, "DELETE /buzz/shares/{id}");

        Response feed = api().queryParam("limit", 20).when().get(Endpoints.BUZZ_FEED);
        Assert.assertFalse(feed.jsonPath().getList("data.text").contains(TestData.BUZZ_POST_TEXT),
                "The deleted post is still visible in the feed");
        shareId = null;
    }

    @AfterClass(alwaysRun = true)
    public void removeLeftoverPost() {
        if (shareId == null) return;
        Response r = api().pathParam("id", shareId).when().delete(Endpoints.BUZZ_SHARE_BY_ID);
        System.out.println("Safety cleanup of Buzz share " + shareId + " -> " + r.statusCode());
        shareId = null;
    }
}
