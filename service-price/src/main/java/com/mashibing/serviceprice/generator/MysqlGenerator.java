package com.mashibing.serviceprice.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * 自动生成代码工具类
 */
public class MysqlGenerator {
    public static void main(String[] args) {

        FastAutoGenerator.create("jdbc:mysql://localhost:3306/service-price?characterEncoding=utf-8&serverTimezone=GMT%2B8",
                "root","041015")
                .globalConfig(builder -> {
                    builder.author("阿漓").fileOverride().outputDir("D:\\00-项目\\gitee\\online-taxi-2022-public\\online-taxi-public\\service-price\\src\\main\\java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.mashibing.serviceprice").pathInfo(Collections.singletonMap(OutputFile.mapperXml,
                            "C:\\Users\\阿漓\\Downloads\\喵喵\\online-taxi-public\\service-price\\src\\main\\java\\com\\mashibing\\serviceprice\\mapper"));
                })
                .strategyConfig(builder -> {
                    builder.addInclude("price_rule");

                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
