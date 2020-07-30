package com.leyou.upload.service;

import com.github.tobato.fastdfs.domain.StorePath;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class UploadService {

    private static final List<String> CONTENT_TYPES = Arrays.asList("image/gif","image/jpeg");//设置图片的白名单
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadService.class);
    @Autowired
    private FastFileStorageClient storageClient;

    /**
     *文件上传
     * @param file
     * @return
     */
    public String uploadImage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        //StringUtils.substringAfterLast(originalFilename , "."); 获取用.分割后的最后一个子字符串
        //校验文件类型
        String contentType = file.getContentType();
        if (!CONTENT_TYPES.contains(contentType)){
            LOGGER.info("文件类型不合法: {}" , originalFilename);//通过日志输出错误原因,参数也可以使用简单的字符串拼接
            return null;
        }
        //校验文件内容
        try {
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null){
                LOGGER.info("文件内容不合法: {}" , originalFilename);
                return null;
            }
            //保存到服务器
            //file.transferTo(new File("D:\\code\\leyou\\image\\" + originalFilename));
            String ext = StringUtils.substringAfterLast(originalFilename , ".");
            StorePath storePath = this.storageClient.uploadFile(file.getInputStream(), file.getSize(), ext, null);

            //返回url,进行回显
            //return "http://image.leyou.com/" + originalFilename;
            return "http://image.leyou.com/" + storePath.getFullPath();
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.info("服务器内部错误：" + originalFilename);
        }
        return null;
    }
}
