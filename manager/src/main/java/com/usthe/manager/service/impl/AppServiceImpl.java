package com.usthe.manager.service.impl;

import com.google.common.collect.Lists;
import com.usthe.common.entity.job.Job;
import com.usthe.common.entity.job.Metrics;
import com.usthe.common.entity.manager.ParamDefine;
import com.usthe.manager.dao.ParamDefineDao;
import com.usthe.manager.pojo.dto.Hierarchy;
import com.usthe.manager.pojo.dto.ParamDefineDto;
import com.usthe.manager.service.AppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控类型管理实现
 * TODO 暂时将监控配置和参数配置存放内存 之后存入数据库
 *
 * @author tomsun28
 * @date 2021/11/14 17:17
 */
@Service
@Order(value = 1)
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class AppServiceImpl implements AppService, CommandLineRunner {

  private final Map<String,Job> appDefines = new ConcurrentHashMap<>();
  private final Map<String,List<ParamDefine>> paramDefines = new ConcurrentHashMap<>();

  @Autowired
  private ParamDefineDao paramDefineDao;

  @Override
  public List<ParamDefine> getAppParamDefines(String app) {
    List<ParamDefine> params = paramDefines.get(app);
    if (params == null) {
      params = Collections.emptyList();
    }
    return params;
  }

  @Override
  public Job getAppDefine(String app) throws IllegalArgumentException {
    Job appDefine = appDefines.get(app);
    if (appDefine == null) {
      throw new IllegalArgumentException("The app " + app + " not support.");
    }
    return appDefine.clone();
  }

  @Override
  public Map<String,String> getI18nResources(String lang) {
    Map<String,String> i18nMap = new HashMap<>(32);
    for (Job job : appDefines.values()) {
      // todo 暂时只国际化监控类型名称  后面需要支持指标名称
      Map<String,String> name = job.getName();
      if (name != null && !name.isEmpty()) {
        String i18nName = name.get(lang);
        if (i18nName == null) {
          i18nName = name.values().stream().findFirst().get();
        }
        i18nMap.put("monitor.app." + job.getApp(), i18nName);
      }
    }
    return i18nMap;
  }

  @Override
  public List<Hierarchy> getAllAppHierarchy(String lang) {
    List<Hierarchy> hierarchies = new LinkedList<>();
    for (Job job : appDefines.values()) {
      Hierarchy hierarchyApp = new Hierarchy();
      hierarchyApp.setCategory(job.getCategory());
      hierarchyApp.setValue(job.getApp());
      Map<String,String> nameMap = job.getName();
      if (nameMap != null) {
        String i18nName = nameMap.get(lang);
        if (i18nName == null) {
          i18nName = nameMap.values().stream().findFirst().get();
        }
        hierarchyApp.setLabel(i18nName);
      }
      List<Hierarchy> hierarchyMetricList = new LinkedList<>();
      if (job.getMetrics() != null) {
        for (Metrics metrics : job.getMetrics()) {
          Hierarchy hierarchyMetric = new Hierarchy();
          hierarchyMetric.setValue(metrics.getName());
          hierarchyMetric.setLabel(metrics.getName());
          List<Hierarchy> hierarchyFieldList = new LinkedList<>();
          if (metrics.getFields() != null) {
            for (Metrics.Field field : metrics.getFields()) {
              Hierarchy hierarchyField = new Hierarchy();
              hierarchyField.setValue(field.getField());
              hierarchyField.setLabel(field.getField());
              hierarchyField.setIsLeaf(true);
              hierarchyFieldList.add(hierarchyField);
            }
            hierarchyMetric.setChildren(hierarchyFieldList);
          }
          hierarchyMetricList.add(hierarchyMetric);
        }
      }
      hierarchyApp.setChildren(hierarchyMetricList);
      hierarchies.add(hierarchyApp);
    }
    return hierarchies;
  }

  @Override
  public void run(String... args)  {
    // 读取app定义配置加载到内存中 define/app/*.yml
    Yaml yaml = new Yaml();

    List<String> pathList = Lists
        .newArrayList("A-example.yml", "api.yml", "fullsite.yml", "linux.yml",
            "mariadb.yml", "mysql.yml", "oracle.yml", "ping.yml", "port.yml",
            "postgresql.yml", "sqlserver.yml", "telnet.yml", "website.yml");

    for (String path : pathList) {
      try {
        String defineAppPath = "define/app/" + path;
        String defineParamPath = "define/param/" + path;
        InputStream appInputStream = this.getClass().getClassLoader()
            .getResource(defineAppPath).openStream();
        Job app = yaml.loadAs(appInputStream, Job.class);
        appDefines.put(app.getApp().toLowerCase(), app);
        InputStream paraInputStream = this.getClass().getClassLoader()
            .getResource(defineParamPath).openStream();
        ParamDefineDto paramDefine = yaml.loadAs(paraInputStream, ParamDefineDto.class);
        paramDefines.put(paramDefine.getApp().toLowerCase(),
            paramDefine.getParam());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

  }

  //
  //  String classpath = this.getClass().getClassLoader().getResource("define")
  //      .getPath();
  //  String defineAppPath = classpath + File.separator + "app";
  //
  //  File directory = new File(defineAppPath);
  //  if (!directory.exists() || directory.listFiles() == null) {
  //    classpath = this.getClass().getResource(File.separator).getPath();
  //    defineAppPath =
  //        classpath + File.separator + "define" + File.separator + "app";
  //    directory = new File(defineAppPath);
  //    if (!directory.exists() || directory.listFiles() == null) {
  //      throw new IllegalArgumentException(
  //          "define app directory not exist: " + defineAppPath);
  //    }
  //  }
  //  log.info("query define path {}", defineAppPath);
  //  for (File appFile : Objects.requireNonNull(directory.listFiles())) {
  //    if (appFile.exists()) {
  //      try (FileInputStream fileInputStream = new FileInputStream(appFile)) {
  //        Job app = yaml.loadAs(fileInputStream, Job.class);
  //        appDefines.put(app.getApp().toLowerCase(), app);
  //      } catch (IOException e) {
  //        log.error(e.getMessage(), e);
  //        throw new IOException(e);
  //      }
  //    }
  //  }
  //  // 读取监控参数定义配置加载到数据库中 define/param/*.yml
  //  String defineParamPath =
  //      classpath + File.separator + "define" + File.separator + "param";
  //  directory = new File(defineParamPath);
  //  if (!directory.exists() || directory.listFiles() == null) {
  //    throw new IllegalArgumentException(
  //        "define param directory not exist: " + defineParamPath);
  //  }
  //  for (File appFile : Objects.requireNonNull(directory.listFiles())) {
  //    if (appFile.exists()) {
  //      try (FileInputStream fileInputStream = new FileInputStream(appFile)) {
  //        ParamDefineDto paramDefine = yaml
  //            .loadAs(fileInputStream, ParamDefineDto.class);
  //        paramDefines
  //            .put(paramDefine.getApp().toLowerCase(), paramDefine.getParam());
  //      } catch (IOException e) {
  //        log.error(e.getMessage(), e);
  //        throw new IOException(e);
  //      }
  //    }
  //  }
  //}
}
