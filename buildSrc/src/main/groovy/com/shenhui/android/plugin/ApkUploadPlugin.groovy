package com.shenhui.android.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import groovy.json.internal.LazyMap
import groovyx.net.http.HTTPBuilder
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.protocol.HTTP
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

import java.nio.charset.Charset

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

class ApkUploadPlugin implements Plugin<Project> {

    private static final String PGYER_URL = "https://qiniu-storage.pgyer.com/"
    private static final String PGYER_UPLOAD_PATH = "/apiv1/app/upload"
    private static final String DING_TALK_URL = "https://oapi.dingtalk.com/"
    private static final String DING_TALK_SEND_PATH = "/robot/send";

    @Override
    void apply(Project project) {
        project.extensions.create("uploadInfo", ApkUploadExtensions)
        getAndroidVariants(project).all {
            variant ->
                def variantName = variant.name.capitalize()
                project.task("upload${variantName}") doLast {
                    def apkFile = variant.outputs[0].outputFile.getAbsolutePath()
                    uploadApk(project, apkFile)
                } dependsOn project.tasks.getByName("assemble${variantName}")
        }
    }

    private void uploadApk(Project project, String apkFile) {
        def desc
        if (project.hasProperty("desc")) {
            desc = project.properties.get("desc")
        } else {
            desc = "no desc"
        }
        def http = new HTTPBuilder(PGYER_URL)
        http.request(POST, JSON) { req ->
            uri.path = PGYER_UPLOAD_PATH
            MultipartEntity entity = new MultipartEntity()
            entity.addPart("file", new FileBody(new File(apkFile)))
            entity.addPart("uKey", new StringBody(project.uploadInfo.userKey))
            entity.addPart("_api_key", new StringBody(project.uploadInfo.apiKey))
            entity.addPart("updateDescription", new StringBody(desc, Charset.forName(HTTP.UTF_8)))
            req.entity = entity
            requestContentType = 'multipart/form-data'
            response.success = { resp, json ->
                assert resp.status == 200
                if (json.code == 0) {
                    println "pgyer upload success"
                    println "--------------------"
                    uploadSuc(project, json.data)
                } else {
                    println json.message
                }
            }
            response.failure = { resp ->
                println "pgyer upload fail, ${resp.status}"
            }
        }
    }

    private void uploadSuc(Project project, LazyMap data) {
        def http = new HTTPBuilder(DING_TALK_URL)
        http.request(POST, JSON) { req ->
            uri.path = DING_TALK_SEND_PATH
            uri.query = ["access_token": project.uploadInfo.dingTalkAccessToken]
            body = "{\n" +
                    "    \"actionCard\": {\n" +
                    "        \"title\": \"Android：${data.appName}\", \n" +
                    "        \"text\": \"![screenshot](${data.appQRCodeURL}) \\n #### **Android**：${data.appName} \\n\\n - 版本信息：${data.appVersion} \\n - 应用大小：${FileSizeUtil.getPrintSize(Long.valueOf(data.appFileSize))} \\n - 更新时间：${data.appUpdated} \\n - 更新内容：${data.appUpdateDescription}\", \n" +
                    "        \"hideAvatar\": \"0\", \n" +
                    "        \"btnOrientation\": \"0\", \n" +
                    "        \"singleTitle\" : \"点击下载最新应用包\",\n" +
                    "        \"singleURL\" : \"https://www.pgyer.com/${data.appShortcutUrl}\"\n" +
                    "    }, \n" +
                    "    \"msgtype\": \"actionCard\"\n" +
                    "}"
            response.success = { resp, json ->
                assert resp.status == 200
                if (json.errcode == 0) {
                    println "ding message send success"
                } else {
                    println json.errmsg
                }
            }
            response.failure = { resp ->
                println "ding message send fail, ${resp.status}"
            }
        }
    }

    private static
    final String sPluginMisConfiguredErrorMessage = "Plugin requires the 'android' plugin to be configured."

    /**
     * get android variant list of the project
     * @param project the compiling project
     * @return android variants
     */
    private static DomainObjectCollection<BaseVariant> getAndroidVariants(Project project) {

        if (project.getPlugins().hasPlugin(AppPlugin)) {
            return project.getPlugins().getPlugin(AppPlugin).extension.applicationVariants
        }
        throw new ProjectConfigurationException(sPluginMisConfiguredErrorMessage, null)
    }
}