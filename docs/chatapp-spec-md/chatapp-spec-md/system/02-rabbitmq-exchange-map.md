# **PHỤ LỤC B – RABBITMQ EXCHANGE MAP**

|  |  |  |  |
| :-: | :-: | :-: | :-: |
| **Exchange** | **Type** | **Publisher** | **Routing Keys** |
| **user.exchange** | topic | Identity Svc | user.registered, user.profile_updated, user.blocked, friend.request_sent, friend.accepted, friend.removed, user.block_set, user.block_removed |
| **chat.exchange** | topic | Messaging Svc | message.sent, message.delivered, message.seen, message.deleted, message.reacted, message.pinned, message.mention, pending.received, pending.accepted |
| **group.exchange** | topic | Group Svc | group.member_joined, group.member_removed, group.role_changed, group.join_request, group.deleted |
| **presence.exchange** | fanout | Presence Svc | presence.online, presence.offline, presence.away, presence.typing_start, presence.typing_stop |
| **notification.exchange** | topic | Notification Svc | notification.new, notification.read |
| **call.exchange** | topic | Call Svc | call.initiated, call.answered, call.ended, call.missed, call.rejected, call.participant_update |
| **social.exchange** | topic | Social Svc | social.post_created, social.post_reacted, social.post_commented, social.comment_replied, social.tag, social.story_created |
| **media.exchange** | topic | Media Svc | media.upload_completed, media.processing_done, media.quarantine |
