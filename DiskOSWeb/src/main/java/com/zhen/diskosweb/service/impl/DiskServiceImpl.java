package com.zhen.diskosweb.service.impl;

import com.zhen.diskosweb.common.entity.ServiceResult;
import com.zhen.diskosweb.common.enums.Status;
import com.zhen.diskosweb.dto.LoginDto;
import com.zhen.diskosweb.entity.Disk;
import com.zhen.diskosweb.entity.other.SelfInfo;
import com.zhen.diskosweb.entity.other.User;
import com.zhen.diskosweb.entity.block.data.FCBBlock;
import com.zhen.diskosweb.service.DiskService;
import lombok.Data;

import java.util.List;

@Data
public class DiskServiceImpl implements DiskService {

    private Disk disk;

    // 该用户的个人信息
    private SelfInfo info = new SelfInfo();

    // 该用户的 service 编号
    private static int code = 0;

    public DiskServiceImpl(Disk disk) {
        this.disk = disk;
    }

    @Override
    public ServiceResult loginDisk(LoginDto loginDto) {
        String username = loginDto.getUsername();
        String password = loginDto.getPassword();

        List<User> userList = disk.getUserList();
        boolean isLogin = false;
        for (User user : userList) {
            // 匹配到用户名
            if (user.getUsername().equals(username)) {
                // 密码正确
                if (user.getPassword().equals(password)) {
                    if (user.isOnline()) {
                        return new ServiceResult(Status.LOGIN_FAIL, "该账号仍在线，不可登录");
                    }
                    info.setNowPath("/");
                    info.setNowFCBBlock((FCBBlock) disk.getDataBlockByINodeIndex(disk.getSuperBlock().getRootFCB().getINodeIndex()));
                    info.setNowUser(user);
                    info.setNowPermission(user.getPermission());
                    info.getNowUser().setOnline(true);
                    isLogin = true;
                    break;
                }
            }
        }
        if (!isLogin) {
            return new ServiceResult(Status.LOGIN_FAIL, "登录失败，账号或密码错误");
        }
        return new ServiceResult(Status.SUCCESS, String.valueOf(code++));
    }


    /**
     * 指令的处理入口
     * @param input 用户输入的字符串指令
     */
    @Override
    public ServiceResult diskEntrance(String input) {
        // 简化指令字符串：去除多余空格和头尾空格
        String command = input.replaceAll(" +", " ").trim();

        String[] commandList = command.split(" ");
        int commandLength = commandList.length;

        ServiceResult serviceResult = null;

        // 操作前将信息设置为自己的
        disk.setInfo(info);

        switch (commandList[0]) {
            // 显示目录
            case "ls":
                if (commandLength == 1)
                    serviceResult = disk.showDir();
                else if (commandLength == 2) {
                    if ("/s".equals(commandList[1])) {
                        serviceResult = disk.showAllDir("");
                    }
                    else {
                        serviceResult = disk.showDir(commandList[1]);
                    }
                }
                else if (commandLength == 3 && "/s".equals(commandList[2])) {
                    serviceResult = disk.showAllDir(commandList[1]);
                }
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 切换目录
            case "cd":
                if (commandLength == 2)
                    serviceResult = disk.changeDir(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 创建目录
            case "mkdir":
                if (commandLength == 2)
                    serviceResult = disk.makeDirOrFile(commandList[1], true);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 创建文件
            case "touch":
                if (commandLength == 2)
                    serviceResult = disk.makeDirOrFile(commandList[1], false);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;


            // 重定向
            case "echo":
                if (commandLength == 4 && ">".equals(commandList[2])) {
                    String data = commandList[1].substring(1, commandList[1].length() - 1);
                    serviceResult = disk.echoToFile(commandList[3], data);
                }
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 显示文件内容
            case "cat":
                if (commandLength == 2)
                    serviceResult = disk.catFile(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 删除文件
            case "del":
                if (commandLength == 2)
                    serviceResult = disk.removeFile(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 删除文件夹
            case "rd":
                if (commandLength == 2)
                    serviceResult = disk.removeDir(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 直接删除文件夹，不需要确认
            case "directremove":
                if (commandLength == 2)
                    serviceResult = disk.directRemoveDir(commandList[1]);
                break;

            // 修改权限
            case "chmod":
                if (commandLength == 3)
                    serviceResult = disk.changeFileMode(commandList[1], commandList[2]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 拷贝文件
            case "copy":
                if (commandLength == 3) {
                    // 如果两个地址都为系统外地址，则报错
                    if (commandList[1].startsWith("<host>") && commandList[2].startsWith("<host>"))
                        serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                    else if (commandList[1].startsWith("<host>"))
                        serviceResult = disk.copyFromOutToIn(commandList[1].substring(6), commandList[2]);
                    else if (commandList[2].startsWith("<host>"))
                        serviceResult = disk.copyFromInToOut(commandList[1], commandList[2].substring(6));
                    else if (!commandList[1].startsWith("<host>") && !commandList[2].startsWith("<host>"))
                        serviceResult = disk.copyFromInToIn(commandList[1], commandList[2]);
                }
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 显示当前路径
            case "pwd":
                if (commandLength == 1)
                    serviceResult = disk.getPwd();
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 显示系统信息
            case "info":
                if (commandLength == 1)
                    serviceResult = disk.showInfo();
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 创建普通用户
            case "cuser":
                if (commandLength == 2)
                    serviceResult = disk.createUser(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 创建管理员
            case "cadmin":
                if (commandLength == 2)
                    serviceResult = disk.createAdmin(commandList[1]);
                else
                    serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
                break;

            // 退出系统
            case "exit":
                disk.logout();
                serviceResult = new ServiceResult(Status.LOGOUT_SUCCESS, "注销成功");
                break;

            // 默认
            default:
                serviceResult = new ServiceResult(Status.COMMAND_ERROR, "输入命令有误");
        }

        return serviceResult;

    }

}
