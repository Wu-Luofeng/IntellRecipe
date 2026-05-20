package com.springboot.intellrecipe.controller;

import com.springboot.intellrecipe.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Value("${upload.dir:${user.home}/IntellRecipe/uploads}")
    private String uploadDir;

    @Value("${upload.url-prefix:http://localhost/uploads}")
    private String urlPrefix;

    /**
     * 上传图片，返回可访问的 URL
     */
    @PostMapping("/img")
    public Result<String> uploadImg(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.fail("只支持图片格式");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.fail("图片大小不能超过 5MB");
        }

        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            File dir = new File(uploadDir + "/" + datePath);
            if (!dir.exists() && !dir.mkdirs()) {
                log.error("无法创建目录: {}", dir.getAbsolutePath());
                return Result.fail("服务器存储错误");
            }

            String ext = getExt(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            File dest = new File(dir, filename);
            file.transferTo(dest);

            String url = urlPrefix + "/" + datePath + "/" + filename;
            log.info("图片上传成功: {}", url);
            return Result.ok(url);
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return Result.fail("上传失败，请稍后重试");
        }
    }

    private String getExt(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}
