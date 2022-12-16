package com.zhen.diskosweb.controller;

import com.zhen.diskosweb.common.entity.ServiceResult;
import com.zhen.diskosweb.common.enums.Status;
import com.zhen.diskosweb.dto.CommandDto;
import com.zhen.diskosweb.dto.LoginDto;
import com.zhen.diskosweb.entity.Disk;
import com.zhen.diskosweb.service.DiskService;
import com.zhen.diskosweb.service.impl.DiskServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/disk")
public class DiskController {

    // 存储用户进程
    List<DiskService> serviceThreadList = new ArrayList<>();

    // 硬盘只加载一次 / 每次执行完指令都需要保存
    Disk disk;

    // 记录硬盘是否已经加载
    private boolean isLoad = false;

    @PostMapping()
    public ServiceResult diskEntrance(@RequestBody CommandDto commandDto) throws IOException {
        // 编号对应的就是其在数组中的位置
        int code = commandDto.getCode();
        DiskService diskService = serviceThreadList.get(code);
        if ("".equals(commandDto.getInput()))
            return new ServiceResult(Status.SUCCESS, "");
        ServiceResult result = diskService.diskEntrance(commandDto.getInput());
        // 保存
        saveOS();
        String msg = result.getMsg();
        // 如果最后一个不是换行符，则需要添加
        if (!"\n".equals(msg.substring(msg.length() - 1)))
            result.setMsg(result.getMsg() + "\n");
        return result;
    }

    @PostMapping("/login")
    public ServiceResult loginDisk(@RequestBody LoginDto loginDto) throws IOException, ClassNotFoundException {
        if (!isLoad) {
            loadOS();
            disk.getUserList().get(0).setOnline(false);
            isLoad = true;
        }
        DiskServiceImpl diskService = new DiskServiceImpl(disk);
        ServiceResult result = diskService.loginDisk(loginDto);
        // 如果登录成功，则需要将其加入到用户数组中进行管理
        if (result.getStatus() == Status.SUCCESS) {
            serviceThreadList.add(diskService);
        }
        return result;
    }

    /**
     * 加载硬盘
     */
    public void loadOS() throws IOException, ClassNotFoundException {
        FileInputStream is = new FileInputStream("C:\\Users\\zengzhen\\Desktop\\diskOS.txt");
        ObjectInputStream ois = new ObjectInputStream(is);
        disk = (Disk)ois.readObject();
    }
    /**
     * 保存硬盘
     */
    public void saveOS() throws IOException {
        FileOutputStream os = new FileOutputStream("C:\\Users\\zengzhen\\Desktop\\diskOS.txt");
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(disk);
        oos.flush();
        oos.close();
    }

}
