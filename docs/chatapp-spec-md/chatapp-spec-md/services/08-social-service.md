# **SERVICE 8 – SOCIAL SERVICE**

|  |  |
| :-: | :-: |
| **Ngôn ngữ** | Java 25 / Spring Boot 4 |
| **Database** | PostgreSQL (posts, reactions, comments) + MongoDB (story views, feed cache) |
| **Cache** | Redis (story list, post feed, reaction count) |

## **8.1 Chức năng đầy đủ**

|  |  |  |
| :-: | :-: | :-: |
| **#** | **Chức năng** | **Mô tả nghiệp vụ chi tiết** |
| **1** | **Đăng story (ảnh/video/text)** | Tạo story record, media_url từ Media Service, expires_at = now + 24h. |
| **2** | **Xem story bạn bè** | Trả danh sách story chưa hết hạn theo privacy (all_friends / close_friends). Insert story_view. |
| **3** | **React story** | Reaction quick emoji vào story → tạo message trong DM (story_reply). |
| **4** | **Nhắn tin vào story** | Gửi text reply → tạo MESSAGE_STORY_REPLY trong Messaging Service. |
| **5** | **Viewers list** | Chủ story xem ai đã xem (story_views). |
| **6** | **Ghim story (Highlight)** | Set is_highlighted=true, đặt highlight_title. Hiển thị vĩnh viễn trên profile. |
| **7** | **Xoá story** | Soft delete trước khi expire. |
| **8** | **Đăng bài viết (Post)** | Tạo post với text, media_urls (từ Media), visibility, tagged_user_ids. |
| **9** | **Chỉnh sửa bài viết** | Update content, is_edited=true. |
| **10** | **Xoá bài viết** | Soft delete. |
| **11** | **React bài viết** | Upsert post_reactions (1 user 1 loại react). Update reaction_count cache. |
| **12** | **Bình luận** | Tạo post_comments, update comment_count. |
| **13** | **Reply comment** | parent_comment_id không null → nested reply. |
| **14** | **Xoá comment** | Soft delete comment. |
| **15** | **Chia sẻ bài viết** | Tạo post mới với shared_from_post_id reference. |
| **16** | **Báo cáo vi phạm** | Tạo content_reports, notify admin. |
| **17** | **Tag bạn bè trong bài** | tagged_user_ids → publish social.tag event → Notification. |
| **18** | **Privacy bài viết** | PUBLIC / FRIENDS / ONLY_ME – filter trước khi trả về. |
| **19** | **Feed bạn bè** | Trả posts của bạn bè sorted by created_at, pagination cursor. |
| **20** | **Xem profile posts** | Posts của 1 user theo visibility + friendship status. |

## **8.2 Enums**

**Enum: PostVisibility**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **PUBLIC** | Mọi người xem được |
| **FRIENDS** | Chỉ bạn bè |
| **ONLY_ME** | Chỉ mình tôi |

**Enum: ReactionEmoji**

|  |  |
| :-: | :-: |
| **Giá trị** | **Ý nghĩa** |
| **LIKE** | ð Thích |
| **LOVE** | ❤️ Yêu thích |
| **HAHA** | ð Haha |
| **WOW** | ð® Wow |
| **SAD** | ð¢ Buồn |
| **ANGRY** | ð  Tức giận |

## **8.3 Database Schema**

### **ð stories  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | NO | – | Tác giả |
| media_url | VARCHAR(500) | YES | NULL | CDN URL ảnh/video |
| media_type | VARCHAR(10) | NO | – | IMAGE / VIDEO / TEXT |
| caption | TEXT | YES | NULL | Chú thích |
| text_content | TEXT | YES | NULL | Nội dung text (nếu type=TEXT) |
| visibility | VARCHAR(20) | NO | FRIENDS_ONLY | Enum PrivacyVisibility |
| is_highlighted | BOOLEAN | NO | false | Ghim vào profile |
| highlight_title | VARCHAR(50) | YES | NULL | Tiêu đề highlight |
| view_count | INT | NO | 0 | Cache số lượt xem |
| expires_at | TIMESTAMPTZ | NO | – | UTC = created_at + 24h |
| is_deleted | BOOLEAN | NO | false | Soft delete |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð story_views  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **story_id** | UUID FK→stories | NO | – | Story |
| **viewer_id** | UUID FK→users | NO | – | Người xem – UNIQUE(story_id, viewer_id) |
| viewed_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð posts  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **user_id** | UUID FK→users | NO | – | Tác giả |
| content | TEXT | YES | NULL | Nội dung văn bản |
| media_upload_ids | UUID[] | NO | {} | Array upload_id Media Service |
| media_urls | TEXT[] | NO | {} | Array CDN URL (sync từ media.upload_completed) |
| visibility | VARCHAR(10) | NO | FRIENDS | Enum PostVisibility |
| tagged_user_ids | UUID[] | NO | {} | Bạn bè được tag |
| **shared_from_post_id** | UUID FK→posts | YES | NULL | Share từ post nào |
| reaction_count | INT | NO | 0 | Denormalized counter |
| comment_count | INT | NO | 0 | Denormalized counter |
| share_count | INT | NO | 0 | Denormalized counter |
| is_edited | BOOLEAN | NO | false | Đã chỉnh sửa |
| is_deleted | BOOLEAN | NO | false | Soft delete |
| deleted_at | TIMESTAMPTZ | YES | NULL | UTC |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð post_reactions  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **post_id** | UUID FK→posts | NO | – | Bài viết – UNIQUE(post_id, user_id) |
| **user_id** | UUID FK→users | NO | – | Người react |
| emoji | VARCHAR(10) | NO | – | Enum ReactionEmoji |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð post_comments  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **post_id** | UUID FK→posts | NO | – | Bài viết |
| **user_id** | UUID FK→users | NO | – | Người bình luận |
| **parent_comment_id** | UUID FK→post_comments | YES | NULL | Reply to – null nếu top-level |
| content | TEXT | NO | – | Nội dung |
| is_deleted | BOOLEAN | NO | false | Soft delete |
| created_at | TIMESTAMPTZ | NO | now() | UTC |
| updated_at | TIMESTAMPTZ | NO | now() | UTC |

### **ð content_reports  [PostgreSQL]**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Column** | **Type** | **Null** | **Default** | **Mô tả** |
| **id** | UUID | NO | gen_random_uuid() | PK |
| **reporter_id** | UUID FK→users | NO | – | Người báo cáo |
| content_type | VARCHAR(10) | NO | – | post / comment / story |
| **content_id** | UUID | NO | – | ID nội dung bị báo cáo |
| reason | VARCHAR(50) | NO | – | SPAM / VIOLENCE / NUDITY / HATE / OTHER |
| description | TEXT | YES | NULL | Mô tả thêm |
| status | VARCHAR(15) | NO | PENDING | PENDING / REVIEWED / ACTIONED / DISMISSED |
| reviewed_by | UUID | YES | NULL | Admin xử lý |
| reviewed_at | TIMESTAMPTZ | YES | NULL | UTC |
| created_at | TIMESTAMPTZ | NO | now() | UTC |

## **8.4 RabbitMQ Events**

|  |  |  |  |  |
| :-: | :-: | :-: | :-: | :-: |
| **Event Name** | **Exchange** | **Routing Key** | **Trigger khi** | **Consumer** |
| social.post_created | social.exchange | social.post_created | Đăng bài viết | Notification (bạn bè), Search reindex |
| social.post_reacted | social.exchange | social.post_reacted | React bài viết | Notification (tác giả), WS GW |
| social.post_commented | social.exchange | social.post_commented | Bình luận | Notification, WS GW |
| social.comment_replied | social.exchange | social.comment_replied | Reply comment | Notification |
| social.tag | social.exchange | social.tag | Tag bạn trong bài | Notification (tagged users) |
| social.story_created | social.exchange | social.story_created | Đăng story | WS GW → badge update bạn bè |

## **8.5 Cấu trúc thư mục**

social-service/

├── src/main/java/com/chatapp/social/

│   ├── controller/

│   │   ├── StoryController.java

│   │   ├── PostController.java

│   │   └── CommentController.java

│   ├── service/

│   │   ├── StoryService.java

│   │   ├── PostService.java

│   │   ├── ReactionService.java

│   │   ├── CommentService.java

│   │   └── FeedService.java

│   ├── domain/

│   │   ├── entity/

│   │   ├── repository/

│   │   └── enums/   # PostVisibility, ReactionEmoji

│   ├── dto/

│   │   └── response/  # PublicPostDTO, PublicStoryDTO

│   ├── event/

│   └── outbox/

└── pom.xml
