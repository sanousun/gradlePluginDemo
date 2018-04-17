## gradle 插件
将生成的 apk 上传到蒲公英上，然后发送钉钉消息

build.gradle 配置
```
// 只有装载了 application 插件才会生成 apk 包
apply plugin: 'com.android.application'
// 自定义的插件，具体代码在 buildSrc 中
apply plugin: 'com.shenhui.android.apkUploadPlugin'

// 上传信息
uploadInfo {
    userKey = PGYER_USER_KEY // 蒲公英的用户 key
    apiKey = PGYER_API_KEY // 蒲公英的 api key
    dingTalkAccessToken = DING_TALK_ACCESS_TOKEN // 钉钉的 webhook token
}
```
编译后执行 .gradlew upload${productFlovars}${buildType} 即可

可以在 gradle->root->Task->others 里面看到对应的任务