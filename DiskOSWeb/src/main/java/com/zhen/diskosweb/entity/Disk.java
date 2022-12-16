package com.zhen.diskosweb.entity;

import com.zhen.diskosweb.common.entity.Result;
import com.zhen.diskosweb.common.entity.ServiceResult;
import com.zhen.diskosweb.common.enums.PermissionCode;
import com.zhen.diskosweb.common.enums.Status;
import com.zhen.diskosweb.entity.blockContain.FCB;
import com.zhen.diskosweb.entity.blockContain.INode;
import com.zhen.diskosweb.entity.block.*;
import com.zhen.diskosweb.entity.block.data.DataBlock;
import com.zhen.diskosweb.entity.block.data.FCBBlock;
import com.zhen.diskosweb.entity.block.data.FileDataBlock;
import com.zhen.diskosweb.entity.other.PermissionRecord;
import com.zhen.diskosweb.entity.other.SelfInfo;
import com.zhen.diskosweb.entity.other.User;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 *  磁盘：
 *      磁盘数组其内容包括：
 *          1、SuperBlock
 *              第0块
 *          2、INode
 *              第1块 - 第25600块
 *          3、BitMap 记录空闲盘块
 *              第25601块 - 第25700块 （100）块
 *                  前25块：用来记录 INodeBlock 的空闲情况
 *                  后74块：用来记录 DataBlock 的空闲情况
 *          4、FAT 文件分配表（400块）
 *              第25701块 - 第26100块
 *                  实际上用不了那么多块，但是凑整数分配更方便
 *                  FAT的序号和 dataBlock 中同步，多出来的不管了
 *          5、数据区
 *              第26101块 - 102399块
 */
@Data
@ToString
public class Disk implements Serializable {

    private static final long serialVersionUID = -6601903208557464574L;

    // 磁盘数组的长度 (即磁盘块数)
    private final int LIST_SIZE = 102400;

    // iNode节点总个数
    private final int INODE_TOTAL_COUNT = 25600 * 4;

    // 数据盘块总个数
    private final int DATA_TOTAL_COUNT = 76299;

    // 磁盘数组
    private BasicBlock[] blockList = new BasicBlock[0];

    private SuperBlock[] superBlockList = new SuperBlock[1];
    private SuperBlock superBlock;
    private INodeBlock[] iNodeBlockList = new INodeBlock[25600];
    private Long iNodeCount;
    private BitMapBlock[] bitMapBlocksList = new BitMapBlock[100];
    private FATBlock[] fatBlocksList = new FATBlock[400];
    private DataBlock[] dataBlocksList = new DataBlock[76299];
    private Long dataBlockCount;
    private Long dirDataBlockCount;
    private Long fileDataBlockCount;

    public static final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    SelfInfo info;
    private FCBBlock rootFCBBlock;

    // 该系统的用户列表
    private List<User> userList;

    // 当前在线用户
    private List<User> onlineUsers;
    
    /**
     * 展示系统经信息
     * @return 响应消息
     */
    public ServiceResult showInfo() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("磁盘总块数：").append(LIST_SIZE).append("\n");
        buffer.append("iNode总个数：").append(INODE_TOTAL_COUNT).append("\n");
        buffer.append("已用iNode个数：").append(iNodeCount).append("\n");
        buffer.append("数据块总块数：").append(DATA_TOTAL_COUNT).append("\n");
        buffer.append("已用数据块数：").append(dataBlockCount).append("\n");
        buffer.append("     ———— 目录数据块数：").append(dirDataBlockCount).append("\n");
        buffer.append("     ———— 文件数据块数：").append(fileDataBlockCount).append("\n");
        buffer.append("系统总空间大小：").append(DATA_TOTAL_COUNT * 1024).append("\n");
        buffer.append("系统剩余空间大小：").append((DATA_TOTAL_COUNT - dataBlockCount) * 1024).append("\n");
        buffer.append("系统用户个数：").append(userList.size()).append("\n");
        for (User user : userList)
            buffer.append("     ———— ").append(user.getUsername()).append(user.isOnline() ? "(在线)" : "").append("\n");

        return new ServiceResult(Status.SUCCESS, buffer.toString());
    }

    /**
     * 初始化后 合并数组
     */
    public static <T> T[] concatAll(T[] first, T[]... rest) {
        int totalLength = first.length;
        for (T[] array : rest) {
            totalLength += array.length;
        }
        T[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (T[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    /**
     *  磁盘初始化
     */
    public Disk() {

        // 各分区初始化
        for (int i = 0; i < superBlockList.length; i ++) {
            superBlockList[i] = new SuperBlock();
        }
        for (int i = 0; i < iNodeBlockList.length; i ++) {
            iNodeBlockList[i] = new INodeBlock();
        }
        for (int i = 0; i < bitMapBlocksList.length; i ++) {
            bitMapBlocksList[i] = new BitMapBlock();
        }
        for (int i = 0; i < fatBlocksList.length; i ++) {
            fatBlocksList[i] = new FATBlock();
        }
        for (int i = 0; i < dataBlocksList.length; i ++) {
            dataBlocksList[i] = new DataBlock();
        }
        // 分区合并
        blockList = concatAll(blockList, superBlockList, iNodeBlockList, bitMapBlocksList, fatBlocksList, dataBlocksList);

        superBlock = superBlockList[0];

    }

    /**
     *  初始化各个分区的根目录信息：
     *      1、根目录的 INode 信息（inode编号 = 0）
     *      2、根目录的 bitMap 信息
     */
    public void initDiskBlock() {
        // 初始化根目录的 inode（第0块的第0个）
        INode[] iNodeList = iNodeBlockList[0].getINodeList();
        // 根目录的目录权限：拥有者-root，其他人可读可执行
        PermissionRecord permission = new PermissionRecord("root", PermissionCode.CAN_READ | PermissionCode.CAN_WRITE | PermissionCode.CAN_EXECUTE, PermissionCode.CAN_READ | PermissionCode.CAN_EXECUTE);
        iNodeList[0] = new INode(true, 0, permission, 0);
        // 初始化根目录的 dataBlock
        dataBlocksList[0] = new FCBBlock();
        // 初始化 bitMapBlock 的占用情况
            // 根目录的 inode block没有完全占用，所以不用改
        // bitMapBlocksList[0].getBitMapList()[0] = true;
            // 根目录的 dataBlock
        bitMapBlocksList[25].getBitMapList()[0] = true;
        // 设置当前路径文件夹的 FCB 磁盘块（初始化时默认为根目录）
        // info.getNowFCBBlock() = (FCBBlock) getDataBlockByINodeIndex(superBlock.getRootFCB().getINodeIndex());
        rootFCBBlock = (FCBBlock) getDataBlockByINodeIndex(superBlock.getRootFCB().getINodeIndex());
        // 用户名单添加初始用户 root
        userList = new ArrayList<>();
        userList.add(new User("root", "root", 7, true, false));
        // 初始化统计变量
        iNodeCount = 1L;
        dataBlockCount = 0L;
        dirDataBlockCount = 0L;
        fileDataBlockCount = 0L;
    }

    public ServiceResult getPwd() {
        return new ServiceResult(Status.SUCCESS, info.getNowPath());
    }

    /**
     * 获取有空位的 inode 磁盘块，及其内部列表的空闲位置下标
     * @return [inode磁盘块index，inode磁盘块列表空闲index]
     */
    public int[] getFreeINodeBlock() {
        for(int i = 0; i < superBlock.BITMAP_BLOCK_INODE_CNT; i ++) {
            boolean[] bitMapList = bitMapBlocksList[i].getBitMapList();
            for(int j = 0; j < bitMapList.length; j ++) {
                if(!bitMapList[j]) {
                    int blockIndex = i * 1024 + j;
                    int emptyIndex = iNodeBlockList[blockIndex].getEmptyIndex();
                    return new int[]{blockIndex, emptyIndex};
                }
            }
        }
        return new int[]{-1, -1};
    }

    /**
     * 获取有空位的 dataBlock 磁盘块
     * @return 返回该 dataBlock 磁盘块的 index
     */
    public int getFreeDataBlock() {
        for(int i = 0; i < superBlock.BITMAP_BLOCK_DATA_CNT; i ++) {
            boolean[] bitMapList = bitMapBlocksList[i + superBlock.BITMAP_BLOCK_INODE_CNT].getBitMapList();
            for (int j = 0; j < bitMapList.length; j++) {
                if (!bitMapList[j]) {
                    // 计算空闲数据块的 index
                    int blockIndex = i * 1024 + j;
                    return blockIndex;
                }
            }
        }
        return -1;
    }

    /**
     * 通过 inode 编号获取 inode 对象
     * @param iNodeIndex inode 编号
     * @return INode inode 对象
     */
    public INode getINodeByINodeIndex(int iNodeIndex) {
        int iNodeBlockIndex = iNodeIndex / 4;
        int iNodeBlockListIndex = iNodeIndex % 4;
        INode iNode = iNodeBlockList[iNodeBlockIndex].getINodeList()[iNodeBlockListIndex];
        return iNode;
    }

    /**
     * 通过 inode 编号获取第一块 dataBlock
     * @param iNodeIndex inode 编号
     * @return DataBlock 数据磁盘块
     */
    public DataBlock getDataBlockByINodeIndex(int iNodeIndex) {
        INode iNode = getINodeByINodeIndex(iNodeIndex);
        int dataBlockLocation = iNode.getDataBlockLocation();
        return dataBlocksList[dataBlockLocation];
    }

    /**
     * 设置 BitMap 中 DataBlock 的占用情况
     * @param dataBlockIndex dataBlock 的下标
     * @param isUse 修改为已占用还是未占用
     */
    public void setBitMapOfDataBlock(int dataBlockIndex, boolean isUse) {
        int bitMapBlockIndex = dataBlockIndex / 1024 + superBlock.BITMAP_BLOCK_INODE_CNT;
        int bitMapBlockListIndex = dataBlockIndex % 1024;
        bitMapBlocksList[bitMapBlockIndex].getBitMapList()[bitMapBlockListIndex] = isUse;
    }

    /**
     * 设置 bitMap 中 inodeBlock 的占用情况
     * @param iNodeBlockIndex inodeBlock 的下标（是 inode块 的下标，不是 inode 的下标）
     * @param isUse 修改为已占用还是未占用
     */
    public void setBitMapOfINodeBlock(int iNodeBlockIndex, boolean isUse) {
        int bitMapBlockIndex = iNodeBlockIndex / 1024;
        int bitMapBlockListIndex = iNodeBlockIndex % 1024;
        bitMapBlocksList[bitMapBlockIndex].getBitMapList()[bitMapBlockListIndex] = isUse;
    }

    /**
     * 检查 inodeBlock 是否已经占满。修改 isFull 属性和 bitMap中的值
     * @param iNodeBlockIndex inodeBlock 的下标
     */
    public void checkINodeBlockFull(int iNodeBlockIndex) {
        INodeBlock iNodeBlock = iNodeBlockList[iNodeBlockIndex];
        INode[] iNodeList = iNodeBlock.getINodeList();
        boolean fullFlag = true;
        for (INode iNode : iNodeList) {
            // 如果有空节点，那么说明没满
            if (iNode == null) {
                fullFlag = false;
                iNodeBlock.setFull(false);
                // 修改 bitMap
                setBitMapOfINodeBlock(iNodeBlockIndex, false);
                break;
            }
        }
        if (fullFlag) {
            iNodeBlock.setFull(true);
            setBitMapOfINodeBlock(iNodeBlockIndex, true);
        }
    }

    /**
     * 将字符串路径转变成数组形式，path的格式：
     *      1、绝对路径：/home/src
     *      2、相对路径：home/src
     * @param path 字符串路径
     * @return 第一位是
     */
    public String[] changePathToArray(String path) {
        return path.split("/");
    }

    /**
     * 将 inode 中的保护码转换成字符串形式 rwx
     * @param iNode 文件的 inode
     * @return rwx格式的保护码
     */
    public String changePermissionToStr(INode iNode) {
        PermissionRecord permissions = iNode.getPermissions();
        StringBuffer res = new StringBuffer();
        int ownerPermission = permissions.getOwnerPermission();
        int otherPermission = permissions.getOtherPermission();
        if ((ownerPermission & 4) != 0)
            res.append("r");
        else
            res.append("-");
        if ((ownerPermission & 2) != 0)
            res.append("w");
        else
            res.append("-");
        if ((ownerPermission & 1) != 0)
            res.append("x");
        else
            res.append("-");

        if ((otherPermission & 4) != 0)
            res.append("r");
        else
            res.append("-");
        if ((otherPermission & 2) != 0)
            res.append("w");
        else
            res.append("-");
        if ((otherPermission & 1) != 0)
            res.append("x");
        else
            res.append("-");

        return res.toString();
    }

    /**
     * 显示文件夹：显示当前目录下的信息
     */
    public ServiceResult showDir() {
        if ((info.getNowPermission() & PermissionCode.CAN_READ) == 0) {
            return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有查看当前目录内容的权限");
        }
        StringBuffer msg = new StringBuffer();
        FCB[] fcbList = info.getNowFCBBlock().getFCBList();

        msg.append("文件名      物理地址      保护码      文件拥有者      文件长度      类型\n");
        for (FCB fcb : fcbList) {
            if (fcb != null) {
                int iNodeIndex = fcb.getINodeIndex();
                INode inode = getINodeByINodeIndex(iNodeIndex);
                // 信息
                msg.append(fcb.getFileName()).append("      ").append(inode.getDataBlockLocation()).append("      ").append(changePermissionToStr(inode)).append("      ").append(inode.getPermissions().getOwnerName()).append("      ").append(inode.getFileSize()).append("      ").append(inode.isDir() ? "文件夹" : "文件").append("\n");
            }
        }

        return new ServiceResult(Status.SUCCESS, msg.toString());
    }


    /**
     * 显示文件夹：显示指定目录下的信息
     * @param dirPath 指定目录路径
     * @return 状态码
     */
    public ServiceResult showDir(String dirPath) {
        StringBuffer msg = new StringBuffer();
        FCBBlock temFCBBlock;
        FCB[] fcbList;
        // 获取该目录的父目录的 FCB
        Result fcbResult = getFCBBlockByPath(getSuperDirPath(dirPath), PermissionCode.CAN_EXECUTE);

        // 没有各级父目录的执行权限，就不能读子目录信息
        if (fcbResult.getStatus() == Status.PERMISSION_ERROR)
            return new ServiceResult(fcbResult.getStatus(), "权限不足，您没有查看该目录下文件信息的权限（因为没有父目录的执行权限）");

        // 路径错误
        if(fcbResult.getStatus() == Status.PATH_ERROR)
            return new ServiceResult(Status.PATH_ERROR, "输入路径有误");

        // 获取父目录的 FCBBlock
        temFCBBlock = (FCBBlock) fcbResult.getData();

        boolean isFind = false;
        // 如果 dirPath == "/"，那么getFCBBlockByPath 求出来的就是根目录的 FCBBlock，就不用寻找下一层
        if (!"/".equals(dirPath)) {
            // 在父目录中找到目标目录的信息
            fcbList = temFCBBlock.getFCBList();
            for (FCB fcb : fcbList) {
                // 找到目标文件夹
                if (fcb != null && fcb.getFileName().equals(getFileName(dirPath))) {
                    int iNodeIndex = fcb.getINodeIndex();
                    INode iNode = getINodeByINodeIndex(iNodeIndex);
                    // 检查是否是文件夹
                    if (iNode.isDir()) {
                        isFind = true;
                        temFCBBlock = (FCBBlock) getDataBlockByINodeIndex(iNodeIndex);
                        break;
                    }
                    // 获取目标文件夹的权限
                    int permissionCode = getPermissionCode(iNode);
                    // 没有该目录的 read 权限
                    if ((permissionCode & PermissionCode.CAN_READ) == 0)
                        return new ServiceResult(fcbResult.getStatus(), "权限不足，您没有查看该目录下文件信息的权限");
                }
            }
        }
        // 没有找到目标目录
        if (!isFind)
            return new ServiceResult(Status.PATH_ERROR, "输入路径有误");

        fcbList = temFCBBlock.getFCBList();
        msg.append("文件名      物理地址      保护码      文件拥有者      文件长度      类型\n");
        for (FCB fcb : fcbList) {
            if (fcb != null) {
                int iNodeIndex = fcb.getINodeIndex();
                INode inode = getINodeByINodeIndex(iNodeIndex);
                // 信息
                msg.append(fcb.getFileName()).append("      ").append(inode.getDataBlockLocation()).append("      ").append(changePermissionToStr(inode)).append("      ").append(inode.getPermissions().getOwnerName()).append("      ").append("      ").append(inode.getFileSize()).append("      ").append(inode.isDir() ? "文件夹" : "文件").append("\n");
            }
        }
        return new ServiceResult(Status.SUCCESS, msg.toString());
    }


    /**
     * 递归展示所有目录
     * @param fcbBlock 需要展示目录的 FCBBlock
     * @param path 该目录的路径
     */
    public ServiceResult recursionShow(FCBBlock fcbBlock, String path, StringBuffer msg) {
        // 目录路径
        msg.append("目录路径：").append(path).append("\n");

        msg.append("文件名      物理地址      保护码      文件拥有者      文件长度      类型\n");
        // 先展示该目录的所有信息
        for (FCB fcb : fcbBlock.getFCBList()) {
            if (fcb != null) {
                int iNodeIndex = fcb.getINodeIndex();
                INode inode = getINodeByINodeIndex(iNodeIndex);
                // 展示信息
                msg.append(fcb.getFileName()).append("      ").append(inode.getDataBlockLocation()).append("      ").append(changePermissionToStr(inode)).append(inode.getPermissions().getOwnerName()).append("      ").append("      ").append(inode.getFileSize()).append("      ").append(inode.isDir() ? "文件夹" : "文件").append("\n");
            }
        }
        // 展示完信息再递归展示子目录
        for (FCB fcb : fcbBlock.getFCBList()) {
            if (fcb != null) {
                int iNodeIndex = fcb.getINodeIndex();
                INode inode = getINodeByINodeIndex(iNodeIndex);
                int permissionCode = getPermissionCode(inode);
                // 如果是文件夹，且有读取权限，则继续递归
                if (inode.isDir() && (permissionCode & PermissionCode.CAN_READ) != 0) {
                    FCBBlock childFCBBlock = (FCBBlock) getDataBlockByINodeIndex(iNodeIndex);
                    // 如果目录是根目录，则不需要加分隔符 /
                    String childPath = "/".equals(path) ? path + fcb.getFileName() : path + "/" + fcb.getFileName();
                    msg.append("\n");
                    recursionShow(childFCBBlock, childPath, msg);
                }
            }
        }
        return new ServiceResult(Status.SUCCESS, msg.toString());
    }

    /**
     * 显示所有子目录 /s
     *      1、参数为 ""，说明是要显示当前目录下的所有子目录
     *      2、参数不为 ""，则要显示指定目录下的所有子目录
     * @param dirPath 文件夹路径
     * @return 状态码
     */
    public ServiceResult showAllDir(String dirPath) {
        StringBuffer msg = new StringBuffer();
        FCBBlock temFCBBlock;
        String absolutePath;

        // 显示当前目录下的所有子目录
        if ("".equals(dirPath)) {
            // 如果当前目录都没有读权限，则其子目录也不可读
            if ((info.getNowPermission() & PermissionCode.CAN_READ) == 0) {
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限查看当前目录和其所有子目录的内容");
            }
            temFCBBlock = info.getNowFCBBlock();
            // 展示目录的绝对路径就是当前路径
            absolutePath = info.getNowPath();
        }
        else {
            Result fcbResult = getFCBBlockByPath(dirPath, PermissionCode.CAN_READ);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR){
                return new ServiceResult(fcbResult.getStatus(), "权限不足，您没有权限查看该目录和其所有子目录");
            }
            // 路径错误，找不到
            if(fcbResult.getStatus() == Status.PATH_ERROR){
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            }
            temFCBBlock = (FCBBlock) fcbResult.getData();
            // 如果是绝对路径（第一个字符是 "/"）
            if ("/".equals(dirPath.substring(0, 1)))
                absolutePath = dirPath;
            else
                // 如果当前目录是根目录 /，就不用添加分隔符 "/"
                absolutePath = "/".equals(info.getNowPath()) ? info.getNowPath() + dirPath : info.getNowPath() + "/" + dirPath;
        }
        return recursionShow(temFCBBlock, absolutePath, msg);

    }

    /**
     * 获取上级目录
     * @param path 当前目录
     * @return 上级目录
     */
    public String getSuperDirPath(String path) {
        // 路径是根目录，则直接返回根目录
        if ("/".equals(path)) {
            return "/";
        }
        // 最后一个 "/" 的位置情况：
        int lastIndex = path.lastIndexOf('/');
        // 如：/home，则直接返回根目录
        if (lastIndex == 0) {
            return "/";
        }
        // 说明没有 "/"，是纯的相对路径，那么上级目录就是当前目录
        else if (lastIndex == -1) {
            return info.getNowPath();
        }
        return path.substring(0, lastIndex);
    }

    /**
     * 通过绝对 / 相对路径获取路径末尾的文件名
     * 比如：/home/user1/doc
     *      得到 doc
     * @param path 路径
     * @return 文件名
     */
    public String getFileName(String path) {
        // 最后一个 "/" 的位置情况：
        int lastIndex = path.lastIndexOf('/');
        // home，说明本身就是文件名
        if (lastIndex == -1) {
            return path;
        }
        return path.substring(lastIndex + 1);
    }

    /**
     * 外部文件获取文件名：通过绝对/相对路径获取路径末尾的文件名
     *      得到 doc
     * @param path 路径
     * @return 文件名
     */
    public String getExternalFileName(String path) {
        // 最后一个 "\" 的位置情况：
        int lastIndex = path.lastIndexOf('\\');
        // home，说明本身就是文件名
        if (lastIndex == -1) {
            return path;
        }
        return path.substring(lastIndex + 1);
    }

    /**
     * 根据 INode 中的权限信息查看当前用户的权限
     * @param iNode 目标文件的 inode
     * @return 权限码
     */
    public int getPermissionCode(INode iNode) {
        // 权限校验
        int permissionCode;
        PermissionRecord permission = iNode.getPermissions();
        if (info.getNowUser().getUsername().equals(permission.getOwnerName()))
            permissionCode = permission.getOwnerPermission();
        else
            permissionCode = permission.getOtherPermission();

        return permissionCode;
    }

    /**
     * 通过 绝对/相对路径 获取该目录的 FCBBlock
     *      如果开头是 /...：就从 rootFCBBlock 开始找
     *      否则：就从 info.getNowFCBBlock()开始找
     * @param path 绝对/相对路径
     * @param operate 到该目录下想执行的操作（用来看有没有相应权限）/ 如果不需要对该文件夹进行操作，传入 -1 即可
     * @return 该路径目录的 FCBBlock
     */
    public Result getFCBBlockByPath(String path, int operate) {
        String[] pathArr = changePathToArray(path);
        FCBBlock temFCBBlock;
        // pathArr开始查找的下标：如果是绝对路径，pathArr[0]是""
        int startIndex;
        // 如果是绝对路径
        if ("/".equals(path) || "".equals(pathArr[0])) {
            temFCBBlock = rootFCBBlock;
            startIndex = 1;
        }
        // 如果是相对路径
        else {
            temFCBBlock = info.getNowFCBBlock();
            startIndex = 0;
        }
        // 目录层次循环
        for (int i = startIndex; i < pathArr.length; i ++) {
            FCB[] fcbList = temFCBBlock.getFCBList();
            boolean isFind = false;
            // 在目录的FCB中寻找对应名称的文件夹
            for (FCB fcb : fcbList) {
                // 找到了文件夹
                if(fcb != null && fcb.getFileName().equals(pathArr[i])) {
                    int iNodeIndex = fcb.getINodeIndex();
                    INode iNode = getINodeByINodeIndex(iNodeIndex);
                    // 如果 operate != -1, 则需要判断是否有相应权限
                    if (operate != -1) {
                        int permissionCode = getPermissionCode(iNode);
                        if ((permissionCode & operate) == 0)
                            return new Result(Status.PERMISSION_ERROR, null);
                    }
                    if (iNode.isDir()) {
                        isFind = true;
                        temFCBBlock = (FCBBlock) getDataBlockByINodeIndex(iNodeIndex);
                        break;
                    }
                }
            }
            // 在某一层 FCB中 没有找到对应的文件夹路径，则说明路径错误
            if (!isFind) {
                return new Result(Status.PATH_ERROR, null);
            }
        }
        return new Result(Status.SUCCESS, temFCBBlock);
    }

    /**
     * 改变当前所在目录
     * @param path 目录路径
     */
    public ServiceResult changeDir(String path) {
        // 如果目标路径为 根目录，则不用查找，直接返回 true
        if ("/".equals(path)) {
            info.setNowFCBBlock(rootFCBBlock);
            info.setNowPath("/");
            return new ServiceResult(Status.SUCCESS, "当前目录：" + info.getNowPath());
        }
        // 如果路径为..(上级目录)，且不在根目录，则返回上级目录
        else if ("..".equals(path)) {
            return changeDir(getSuperDirPath(info.getNowPath()));
        }

        String[] pathArr = changePathToArray(path);
        // 通过路径获得目标目录 父目录的 FCBBlock
        Result fcbResult = getFCBBlockByPath(getSuperDirPath(path), PermissionCode.CAN_EXECUTE);
        // 没有访问目标目录的权限
        if (fcbResult.getStatus() == Status.PERMISSION_ERROR) {
            return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限进入该目录，因为没有父目录的执行权限");
        }
        // 路径有误，找不到
        if (fcbResult.getStatus() == Status.PATH_ERROR) {
            return new ServiceResult(Status.PATH_ERROR, "路径有误");
        }

        FCBBlock temFCBBlock = (FCBBlock) fcbResult.getData();
        String dirName = getFileName(path);

        boolean isFind = false;
        INode dirINode = null;

        // 在目标文件夹的父目录中寻找该 dirName 的信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (FCB fcb : fcbList) {
            // 寻找名称为 dirName 的文件夹
            if (fcb != null && fcb.getFileName().equals(dirName)) {
                dirINode = getINodeByINodeIndex(fcb.getINodeIndex());
                // 如果目标是一个文件，则不可以cd
                if (!dirINode.isDir()) {
                    return new ServiceResult(Status.PATH_ERROR, "该路径不是目录");
                }
                // 说明该用户没有对文件操作的权限
                if ((getPermissionCode(dirINode) & PermissionCode.CAN_EXECUTE) == 0) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您没有权限进入该目录");
                }
                temFCBBlock = (FCBBlock) dataBlocksList[dirINode.getDataBlockLocation()];
                isFind = true;
                break;
            }
        }

        if (!isFind)
            return new ServiceResult(Status.DIR_NOT_EXIST_ERROR, "文件夹不存在");

        // 如果是绝对路径
        if ("".equals(pathArr[0]))
            info.setNowPath(path);
        // 如果是相对路径
        else{
            if ("/".equals(info.getNowPath()))
                info.setNowPath(info.getNowPath() + path);
            else
                info.setNowPath(info.getNowPath() + "/" + path);
        }
        // 找到了对应的路径，则将当前的 FCBBlock 赋值为刚才循环找到的 temFCBBlock
        info.setNowFCBBlock(temFCBBlock);
        // 更新当前目录的permission
        info.setNowPermission(getPermissionCode(dirINode));
        return new ServiceResult(Status.SUCCESS, "当前目录：" + info.getNowPath());
    }


    /**
     * 创建 文件夹/文件: 在 当前路径/指定路径 下创建文件/文件夹
     *      1、绝对路径：mkdir /home/user1
     *      2、相对路径（可能是子目录）：mkdir home/user1
     *      3、相对路径（当前目录）：mkdir home
     * 1 和 2 需要使用 getFCBBlockByPath 获取上级 FCBBlock
     * @param filePath 文件夹/文件名称
     */
    public ServiceResult makeDirOrFile(String filePath, boolean isDir) {
        FCBBlock temFCBBlock;
        String dirName;
        // dirPath 中不含有 "/"，说明 dirPath 就是文件/文件夹名
        if (!filePath.contains("/")) {
            if ((info.getNowPermission() & PermissionCode.CAN_WRITE) == 0) {
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限在该目录下创建文件/文件夹");
            }
            temFCBBlock = info.getNowFCBBlock();
            dirName = filePath;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(filePath), PermissionCode.CAN_WRITE);
            // 在目标目录的父目录写权限不足
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR) {
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限在该目录下创建文件/文件夹");
            }
            // 如果 temFCBBlock = null，说明没找到，说明路径错误
            if(fcbResult.getStatus() == Status.PATH_ERROR) {
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            }

            temFCBBlock = (FCBBlock) fcbResult.getData();
            // 获取文件名/文件夹名
            dirName = getFileName(filePath);
        }

        // 检查该目录下有没有同名的文件夹/文件
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (FCB fcb : fcbList) {
            if (fcb != null && fcb.getFileName().equals(dirName))
                return new ServiceResult(Status.REPEAT_NAME_ERROR, "存在同名文件/文件夹");
        }

        // 获取空闲 iNodeBlock 的 index
        int[] freeINodeBlock = getFreeINodeBlock();
        int iNodeBlockIndex = freeINodeBlock[0];
        int iNodeBlockListIndex = freeINodeBlock[1];
        // 获取空闲 dataBlock 的 index
        int freeDataBlockIndex = getFreeDataBlock();
        // 创建 dataBlock：FCBBlock/FileDataBlock
        if (isDir)
            dataBlocksList[freeDataBlockIndex] = new FCBBlock();
        else
            dataBlocksList[freeDataBlockIndex] = new FileDataBlock();
        // 修改 bitMap 中 dataBlock 的情况
        setBitMapOfDataBlock(freeDataBlockIndex, true);


        // 创建新文件夹的 inode
        // 个人权限全开；其他人权限可读可执行
        iNodeBlockList[iNodeBlockIndex].getINodeList()[iNodeBlockListIndex] = new INode(isDir, 0, new PermissionRecord(info.getNowUser().getUsername(), PermissionCode.CAN_READ | PermissionCode.CAN_WRITE | PermissionCode.CAN_EXECUTE, PermissionCode.CAN_READ | PermissionCode.CAN_EXECUTE), freeDataBlockIndex);
        // 更新 inodeBlock 的使用状态，更新 bitMap
        checkINodeBlockFull(iNodeBlockIndex);

        // 为目标文件的父文件夹添加 FCB 记录
        temFCBBlock.addFCB(new FCB(dirName, iNodeBlockIndex * 4 + iNodeBlockListIndex));

        // 统计变量更新
        iNodeCount ++;
        dataBlockCount ++;
        if (isDir)
            dirDataBlockCount ++;
        else
            fileDataBlockCount ++;

        return new ServiceResult(Status.SUCCESS, "创建成功");
    }

    /**
     * 添加文件关联信息
     * @param dataBlockIndex 前一个 dataBlock 的 FAT index
     * @param nextDataBlockIndex 后一个 dataBlock 的 FAT index
     */
    public void setFATData(int dataBlockIndex, int nextDataBlockIndex) {
        fatBlocksList[dataBlockIndex / 256].getFATList()[dataBlockIndex % 256] = nextDataBlockIndex;
    }

    /**
     * 重置 dataBlock：
     *      1、删除 dataBlock 的信息
     *      2、在 FAT 关联表中重置关联信息（设为 -1）
     *      3、重置 bitMap 表中的占用情况（设为 false）
     * @param dataBlockIndex 需要重置的 dataBlock 的 FAT index
     */
    public void resetDataBlock(int dataBlockIndex) {
        dataBlocksList[dataBlockIndex] = new DataBlock();
        fatBlocksList[dataBlockIndex / 256].getFATList()[dataBlockIndex % 256] = -1;
        setBitMapOfDataBlock(dataBlockIndex, false);
        dataBlockCount --;
    }

    /**
     * 释放文件的 dataBlock（除了第一块）
     * @param fileINode 文件的 inode
     */
    public void freeTheDataBlockExceptFirst(INode fileINode) {
        int nowDataBlockIndex = fileINode.getDataBlockLocation();
        // 获取第二块的 dataBlockIndex
        int nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nowDataBlockIndex);
        // 第一块只需要重置 FAT 关联表即可
        fatBlocksList[nowDataBlockIndex / 256].getFATList()[nowDataBlockIndex % 256] = -1;

        while (nextDataBlockIndex != -1) {
            nowDataBlockIndex = nextDataBlockIndex;
            nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nowDataBlockIndex);
            resetDataBlock(nowDataBlockIndex);
            fileDataBlockCount--;
        }
    }

    /**
     * 释放文件的 所有 dataBlock
     * @param fileINode 文件的 inode
     */
    public void freeAllDataBlock(INode fileINode) {
        int nowDataBlockIndex = fileINode.getDataBlockLocation();
        // 获取第二块的 dataBlockIndex
        int nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nowDataBlockIndex);
        resetDataBlock(nowDataBlockIndex);
        fileDataBlockCount --;

        while (nextDataBlockIndex != -1) {
            nowDataBlockIndex = nextDataBlockIndex;
            nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nowDataBlockIndex);
            resetDataBlock(nowDataBlockIndex);
            fileDataBlockCount --;
        }
    }

    /**
     * 向文件中写入字符串（覆盖写入），需要先重置（除了第一块以外的）dataBlock
     * @param fileINode 文件的 inode
     * @param data 字符串数据
     * @return 是否成功
     */
    public boolean writeIntoFile(INode fileINode, String data) {
        // 先转换成字节数组
        byte[] dataBytes = data.getBytes();
        // 字符串转换为字节后的总字节大小
                                                                                                                                                                                                                                                                                                                                                                                                        int dataLength = dataBytes.length;
        int dataBlockCnt = (dataLength / 1024) + 1;

        // 先重置（除了第一块以外的）dataBlock
        freeTheDataBlockExceptFirst(fileINode);

        // 第一块用原本分配的 fileDataBlock
        FileDataBlock firstDataBlock = (FileDataBlock) dataBlocksList[fileINode.getDataBlockLocation()];
        int nowDataBlockIndex;
        synchronized (firstDataBlock) {
            // 如果只有一块，byte 数组则只存 dataLength 长度；否则存满 1024
            firstDataBlock.setData(Arrays.copyOfRange(dataBytes, 0, dataBlockCnt == 1 ? dataLength : 1024));
            nowDataBlockIndex = fileINode.getDataBlockLocation();
        }

        for (int i = 1; i < dataBlockCnt; i++) {
            // 获取空闲数据块的 index
            int freeDataBlockIndex = getFreeDataBlock();
            if (freeDataBlockIndex == -1)
                return false;
            // 设置文件关联信息
            setFATData(nowDataBlockIndex, freeDataBlockIndex);
            // 将空闲数据块初始化成 FileDataBlock
            dataBlocksList[freeDataBlockIndex] = new FileDataBlock();
            FileDataBlock nextFileDataBlock = (FileDataBlock) dataBlocksList[freeDataBlockIndex];
            // 修改 bitMap 中 dataBlock 的情况
            setBitMapOfDataBlock(freeDataBlockIndex, true);
            synchronized (firstDataBlock) {
                // 如果是最后一块
                if (i == dataBlockCnt - 1)
                    nextFileDataBlock.setData(Arrays.copyOfRange(dataBytes, i * 1024, dataLength));
                else
                    nextFileDataBlock.setData(Arrays.copyOfRange(dataBytes, i * 1024, (i + 1) * 1024));
            }
            nowDataBlockIndex = freeDataBlockIndex;
            fileDataBlockCount ++;
            dataBlockCount ++;
        }
        return true;
    }


    /**
     * 更新各层级目录下的文件大小
     * @param fileAbsolutePath 文件路径（一定要是绝对路径）
     * @param fileSize 文件大小
     */
    public void updateFileSize(String fileAbsolutePath, long fileSize) {
        FCBBlock temFCBBlock = rootFCBBlock;
        String[] pathArray = changePathToArray(fileAbsolutePath);
        // 目录层次循环
        for (int i = 1; i < pathArray.length; i ++) {
            FCB[] fcbList = temFCBBlock.getFCBList();
            // 在目录的FCB中寻找对应名称的文件夹
            for (FCB fcb : fcbList) {
                // 如果在删除文件(夹)之后进行文件大小更新，那么最后一个文件(夹)的名称不会被找到，就不会进入下面的 if 语句
                if(fcb != null && fcb.getFileName().equals(pathArray[i])) {
                    int iNodeIndex = fcb.getINodeIndex();
                    INode iNode = getINodeByINodeIndex(iNodeIndex);
                    // 更新文件大小
                    iNode.setFileSize(iNode.getFileSize() + fileSize);
                    // 重定向重复写入同一个文件时，绝对路径的最后一个名称可以被找到，而且是文件，后续不需要继续往下找了，因此需要特判
                    if (i != pathArray.length - 1)
                        temFCBBlock = (FCBBlock) getDataBlockByINodeIndex(iNodeIndex);
                    break;
                }
            }
        }
    }


    /**
     * 将数据(字符串)重定向到某个文件中
     *      1、绝对路径：echo "aaa" > /home/doc
     *      2、相对路径（可能是子目录）：echo "aaa" >  home/doc
     *      3、相对路径（当前目录）：echo "aaa" >  doc
     * 1 和 2 需要使用 getFCBBlockByPath 获取上级 FCBBlock
     * @param filePath 目标文件
     * @param data 数据
     * @return 是否成功
     */
    public ServiceResult echoToFile(String filePath, String data) {
        FCBBlock temFCBBlock;
        String dirName;
        // 文件的绝对路径，用于更新文件大小
        String absolutePath;
        // 如果没有 "/"，那么说明 filePath 就是 文件名
        if (!filePath.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            dirName = filePath;
            absolutePath = info.getNowPath() + "/" + dirName;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(filePath), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR)
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有向该文件重定向的权限，因为父目录没有执行权限");
            // 路径有误，找不到
            if (fcbResult.getStatus() == Status.PATH_ERROR) {
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            }
            temFCBBlock = (FCBBlock) fcbResult.getData();
            // 获取文件名/文件夹名
            dirName = getFileName(filePath);
            if ("/".equals(filePath) || "/".equals(filePath.substring(0, 1)))
                absolutePath = filePath;
            else
                absolutePath = info.getNowPath() + "/" + filePath;
        }

        boolean isFind = false;
        INode fileINode = null;

        // 在目标文件的父目录中寻找该 fileName 的文件信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (FCB fcb : fcbList) {
            // 寻找名称为 fileName 的文件
            if (fcb != null && fcb.getFileName().equals(dirName)) {
                fileINode = getINodeByINodeIndex(fcb.getINodeIndex());
                // 文件夹不可以被写入
                if (fileINode.isDir())
                    return new ServiceResult(Status.DIR_CAN_NOT_WRITE_ERROR, "文件夹不可以写入数据");
                // 说明该用户没有对文件写操作的权限
                if ((getPermissionCode(fileINode) & PermissionCode.CAN_WRITE) == 0)
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您没有向该文件重定向的权限");
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件
        if (!isFind)
            return new ServiceResult(Status.FILE_NOT_EXIST_ERROR, "文件不存在");

        // 开始写入文件
        boolean write = writeIntoFile(fileINode, data);

        // 获取原来的数据大小
        long oldFileSize = fileINode.getFileSize();
        long newFileSize = data.getBytes().length;

        if (write){
            // 写入成功后需要更新文件大小信息
            updateFileSize(absolutePath, newFileSize - oldFileSize);
            return new ServiceResult(Status.SUCCESS, "操作成功");
        }
        // 如果返回 false，说明无空闲数据块了
        else
            return new ServiceResult(Status.NO_FREE_DATA_BLOCK_ERROR, "空闲数据块已用尽");
    }

    /**
     * 从指定的文件路径读取内容
     * @param filePath 文件路径
     * @return 文件内容data
     */
    public ServiceResult catFile(String filePath) {
        FCBBlock temFCBBlock;
        String dirName;
        // 情况3：重定向到当前目录的文件，那么 filePath 就是 文件名
        if (!filePath.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            dirName = filePath;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取（其各级父目录都要可执行）
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(filePath), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR) {
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您没有读取该文件内容的权限，因为父目录没有执行权限");
            }
            // 没找到，说明路径错误
            if(fcbResult.getStatus() == Status.PATH_ERROR)
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            temFCBBlock = (FCBBlock) fcbResult.getData();
            // 获取文件名/文件夹名
            dirName = getFileName(filePath);
        }

        boolean isFind = false;
        INode fileINode = null;

        // 在目标文件的父目录中寻找该 fileName 的文件信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (FCB fcb : fcbList) {
            // 寻找名称为 fileName 的文件
            if (fcb != null && fcb.getFileName().equals(dirName)) {
                fileINode = getINodeByINodeIndex(fcb.getINodeIndex());
                if (fileINode.isDir())
                    return new ServiceResult(Status.DIR_CAN_NOT_CAT, "文件夹不可被读取");
                // 查看用户对该文件的读取去权限
                if ((getPermissionCode(fileINode) & PermissionCode.CAN_READ) == 0) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您没有读取该文件内容的权限");
                }
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件
        if (!isFind)
            return new ServiceResult(Status.FILE_NOT_EXIST_ERROR, "文件不存在");

        return new ServiceResult(Status.SUCCESS, readFromFile(fileINode));

    }

    /**
     * 通过当前的数据 index 结合 FAT表，查询下一块 dataBlock 的位置
     * @param nowDataBlockIndex 当前 dataBlock index
     * @return 下一块 dataBlock 的位置
     */
    public int getNextDataIndexByNowDataBlockIndex(int nowDataBlockIndex) {
        return fatBlocksList[nowDataBlockIndex / 256].getFATList()[nowDataBlockIndex % 256];
    }

    /**
     * 从第一个 inode 开始读取内容，
     * @param iNode 文件的inode
     * @return 字符串结果
     */
    public String readFromFile(INode iNode) {
        String data;
        int nowDataBlockIndex = iNode.getDataBlockLocation();
        FileDataBlock firstDataBlock = (FileDataBlock) dataBlocksList[nowDataBlockIndex];
        byte[] dataByte = firstDataBlock.getData();
        int nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nowDataBlockIndex);
        while(nextDataBlockIndex != -1) {
            FileDataBlock dataBlock = (FileDataBlock) dataBlocksList[nextDataBlockIndex];
            byte[] nextData = dataBlock.getData();
            dataByte = ArrayUtils.addAll(dataByte, nextData);
            nextDataBlockIndex = getNextDataIndexByNowDataBlockIndex(nextDataBlockIndex);
        }
        data = new String(dataByte);
        return data;
    }

    /**
     * 重置 inode：
     *      1、在 inodeBlock 中重置 inode，并设置属性 isFull = false
     *      2、将该 inodeBlock 的 bitMap 值设置为 false
     * @param fileINodeIndex 文件的 inode index
     */
    public void freeINode(int fileINodeIndex) {
        int fileINodeBlockIndex = fileINodeIndex / 4;
        int fileINodeBlockListIndex = fileINodeIndex % 4;
        iNodeBlockList[fileINodeBlockIndex].getINodeList()[fileINodeBlockListIndex] = null;
        // 将 inodeBlock 的 isFull 属性设置为 false
        iNodeBlockList[fileINodeBlockIndex].setFull(false);
        // 将该 inodeBlock 在 bitMap 中的值设置为 false
        setBitMapOfINodeBlock(fileINodeBlockIndex, false);
        iNodeCount --;
    }
    
    /**
     * 通过文件的 inode 删除文件信息：
     *      1、重置所有 dataBLock
     *      2、重置 dataBlock 的 FAT 表（设为 -1）
     *      3、重置 dataBlock 的 bitMap 表（设为 false）
     *      4、重置 inode
     *      5、重置 inode 的 bitMap 表（设为 false）
     * @param fileINodeIndex 文件的 inode index
     * @param fileINode 文件的 inode
     */
    public void deleteFile(int fileINodeIndex, INode fileINode) {
        // 重置所有 dataBlock（包括重置 FAT, bitMap）
        freeAllDataBlock(fileINode);
        // 重置该文件的 inode
        freeINode(fileINodeIndex);
    }

    /**
     * 删除文件
     * @param filePath 文件路径
     * @return 状态码
     */
    public ServiceResult removeFile(String filePath) {
        FCBBlock temFCBBlock;
        String fileName;
        String absolutePath;
        // dirPath 中不含有 "/"，说明 dirPath 就是文件/文件夹名
        if (!filePath.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            fileName = filePath;
            absolutePath = info.getNowPath() + "/" + fileName;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取（父目录不能执行，子目录也不能执行）
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(filePath), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR) {
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件，因为没有父目录的执行权限");
            }
            // 路径有误，找不到
            if (fcbResult.getStatus() == Status.PATH_ERROR) {
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            }
            // 获取文件名/文件夹名
            fileName = getFileName(filePath);
            temFCBBlock = (FCBBlock) fcbResult.getData();
            if ("/".equals(filePath) || "/".equals(filePath.substring(0, 1)))
                absolutePath = filePath;
            else
                absolutePath = info.getNowPath() + "/" + filePath;
        }

        boolean isFind = false;
        int fileINodeIndex = -1;
        INode fileINode = null;
        // 目标文件在父目录 FCBList 的 index
        int superFCBIndex = -1;

        // 在目标文件的父目录中寻找该 fileName 的文件信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (int i = 0; i < fcbList.length; i ++) {
            // 寻找名称为 fileName 的文件
            if (fcbList[i] != null && fcbList[i].getFileName().equals(fileName)) {
                fileINodeIndex = fcbList[i].getINodeIndex();
                fileINode = getINodeByINodeIndex(fcbList[i].getINodeIndex());
                // 标记该文件在 父目录的 FCB 位置
                superFCBIndex = i;
                // 文件夹不能使用该指令删除
                if (fileINode.isDir()) {
                    return new ServiceResult(Status.DIR_CAN_NOT_DELETE_AS_FILE, "文件夹不能通过删除文件的指令删除");
                }
                if ((getPermissionCode(fileINode) & PermissionCode.CAN_EXECUTE) == 0) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件，因为没有该文件的执行权限");
                }
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件
        if (!isFind)
            return new ServiceResult(Status.FILE_NOT_EXIST_ERROR, "文件不存在");

        long fileSize = fileINode.getFileSize();

        // 删除该文件的信息
        deleteFile(fileINodeIndex, fileINode);

        // 从父目录的 FCB 中移除该文件的信息
        fcbList[superFCBIndex] = null;

        // 更新各级目录的文件大小
        updateFileSize(absolutePath, -fileSize);

        return new ServiceResult(Status.SUCCESS, "操作成功");
    }

    /**
     * 通过文件夹的 inode 删除文件夹
     *      1、重置文件夹的 FCBBlock
     *      2、重置 FCBBlock 的 bitMap 表（设为 false）
     *      3、重置 inode
     *      4、重置 inode 的 bitMap 表（设为 false）
     * @param dirINodeIndex 文件夹的 inode index（用于重置 inode 的 bitMap 信息）
     * @param dirINode 文件夹的 inode
     */
    public void deleteDir(int dirINodeIndex, INode dirINode) {
        int fcbBlockIndex = dirINode.getDataBlockLocation();
        // 通过 dataBlock 的编号重置 dataBlock（多了一步重置 FAT 表为 -1，不过没关系）
        resetDataBlock(fcbBlockIndex);
        dirDataBlockCount --;
        // 重置 inode
        freeINode(dirINodeIndex);
    }

    /**
     * 递归删除文件夹的入口
     * @param dirINode 指定文件夹的 inode
     */
    public void recursionDeleteDir(INode dirINode) {
        FCBBlock dirFCBBlock = (FCBBlock) dataBlocksList[dirINode.getDataBlockLocation()];
        FCB[] dirFCBList = dirFCBBlock.getFCBList();
        for (FCB fcb : dirFCBList) {
            if (fcb != null) {
                // 获取文件/文件夹的 inode index
                int childINodeIndex = fcb.getINodeIndex();
                // 获取文件/文件夹的 inode
                INode childINode = getINodeByINodeIndex(childINodeIndex);
                // 如果该子文件是 目录，说明要继续递归
                if (childINode.isDir()) {
                    recursionDeleteDir(childINode);
                    deleteDir(childINodeIndex, childINode);
                }
                // 如果该子文件是 文件，则直接删除
                else {
                    deleteFile(childINodeIndex, childINode);
                }
            }
        }
    }

    /**
     * 删除目录：删除指定目录下的所有文件和子目录（需要确认）
     *      1、绝对路径的目录：/home/user1
     *      2、相对路径的目录：home/user1
     *      3、当前目录下的目录：user1
     * @param dirPath 指定目录的路径
     */
    public ServiceResult removeDir(String dirPath) {
        // 指定目录的父目录
        FCBBlock temFCBBlock;
        // 指定目录的目录名
        String dirName;
        // 指定目录的绝对路径
        String absolutePath;

        // dirPath 中不含有 "/"，说明 dirPath 就是文件/文件夹名
        if (!dirPath.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            dirName = dirPath;
            absolutePath = info.getNowPath() + "/" + dirName;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(dirPath), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR)
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件夹，因为没有父目录的执行权限");
            // 路径有误，找不到
            if (fcbResult.getStatus() == Status.PATH_ERROR)
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            // 获取文件名/文件夹名
            dirName = getFileName(dirPath);
            temFCBBlock = (FCBBlock) fcbResult.getData();
            if ("/".equals(dirPath) || "/".equals(dirPath.substring(0, 1)))
                absolutePath = dirPath;
            else
                absolutePath = info.getNowPath() + "/" + dirPath;
        }

        boolean isFind = false;
        int dirINodeIndex = -1;
        // 目标目录的 inode
        INode dirINode = null;
        // 目标目录在父目录 FCBList 的 index
        int superFCBIndex = -1;

        // 在目标目录的父目录中寻找该 dirName 的文件夹信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (int i = 0; i < fcbList.length; i ++) {
            // 寻找名称为 dirName 的文件夹
            if (fcbList[i] != null && fcbList[i].getFileName().equals(dirName)) {
                dirINodeIndex = fcbList[i].getINodeIndex();
                dirINode = getINodeByINodeIndex(fcbList[i].getINodeIndex());
                // 标记该文件夹在 父目录的 FCB 位置
                superFCBIndex = i;
                // 文件不能使用该指令删除
                if (!dirINode.isDir()){
                    return new ServiceResult(Status.FILE_CAN_NOT_DELETE_AS_DIR, "文件不能通过删除文件夹的指令删除");
                }
                if ((getPermissionCode(dirINode) & PermissionCode.CAN_EXECUTE) == 0) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件夹，因为没有该文件夹的执行权限");
                }
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件夹
        if (!isFind)
            return new ServiceResult(Status.DIR_NOT_EXIST_ERROR, "未找到该名称的文件夹");

        // 找到该文件夹的 inode 后，找到该文件夹的 FCB dataBlock，看看有没有内容，如果有内容需要进行提示
        FCBBlock dirFCBBlock = (FCBBlock) dataBlocksList[dirINode.getDataBlockLocation()];
        FCB[] dirFCBList = dirFCBBlock.getFCBList();

        boolean hasFileOrDir = false;

        for (FCB fcb : dirFCBList) {
            if (fcb != null) {
                hasFileOrDir = true;
                break;
            }
        }
        // 如果文件夹内有内容，则需要提示是否要删除
        if (hasFileOrDir) {
            System.out.print("该文件夹不为空，是否要删除？（yes/no）: ");
            return new ServiceResult(Status.CONFIRM_DELETE_DIR, "该文件夹不为空，是否要删除？（yes/no）: ");
        }
        // 执行删除
        else {
            // 获取该文件夹的文件大小
            long dirFileSize = dirINode.getFileSize();

            // 开始递归删除目录中的内容
            recursionDeleteDir(dirINode);

            // 删除目标目录
            deleteDir(dirINodeIndex, dirINode);

            // 删除目标目录在父目录 FCB 中的信息
            fcbList[superFCBIndex] = null;

            // 更新路径上的文件夹大小
            updateFileSize(absolutePath, -dirFileSize);

            return new ServiceResult(Status.SUCCESS, "操作成功");
        }
    }

    /**
     * 直接删除目录，不用确认
     *      1、绝对路径的目录：/home/user1
     *      2、相对路径的目录：home/user1
     *      3、当前目录下的目录：user1
     * @param dirPath 指定目录的路径
     */
    public ServiceResult directRemoveDir(String dirPath) {
        // 指定目录的父目录
        FCBBlock temFCBBlock;
        // 指定目录的目录名
        String dirName;
        // 指定目录的绝对路径
        String absolutePath;

        // dirPath 中不含有 "/"，说明 dirPath 就是文件/文件夹名
        if (!dirPath.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            dirName = dirPath;
            absolutePath = info.getNowPath() + "/" + dirName;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(dirPath), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR)
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件夹，因为没有父目录的执行权限");
            // 路径有误，找不到
            if (fcbResult.getStatus() == Status.PATH_ERROR)
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            // 获取文件名/文件夹名
            dirName = getFileName(dirPath);
            temFCBBlock = (FCBBlock) fcbResult.getData();
            if ("/".equals(dirPath) || "/".equals(dirPath.substring(0, 1)))
                absolutePath = dirPath;
            else
                absolutePath = info.getNowPath() + "/" + dirPath;
        }

        boolean isFind = false;
        int dirINodeIndex = -1;
        // 目标目录的 inode
        INode dirINode = null;
        // 目标目录在父目录 FCBList 的 index
        int superFCBIndex = -1;

        // 在目标目录的父目录中寻找该 dirName 的文件夹信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (int i = 0; i < fcbList.length; i ++) {
            // 寻找名称为 dirName 的文件夹
            if (fcbList[i] != null && fcbList[i].getFileName().equals(dirName)) {
                dirINodeIndex = fcbList[i].getINodeIndex();
                dirINode = getINodeByINodeIndex(fcbList[i].getINodeIndex());
                // 标记该文件夹在 父目录的 FCB 位置
                superFCBIndex = i;
                // 文件不能使用该指令删除
                if (!dirINode.isDir()){
                    return new ServiceResult(Status.FILE_CAN_NOT_DELETE_AS_DIR, "文件不能通过删除文件夹的指令删除");
                }
                if ((getPermissionCode(dirINode) & PermissionCode.CAN_EXECUTE) == 0) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足，您没有权限删除该文件夹，因为没有该文件夹的执行权限");
                }
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件夹
        if (!isFind)
            return new ServiceResult(Status.DIR_NOT_EXIST_ERROR, "未找到该名称的文件夹");

        // 找到该文件夹的 inode 后，找到该文件夹的 FCB dataBlock，看看有没有内容，如果有内容需要进行提示
        FCBBlock dirFCBBlock = (FCBBlock) dataBlocksList[dirINode.getDataBlockLocation()];
        FCB[] dirFCBList = dirFCBBlock.getFCBList();

        // 获取该文件夹的文件大小
        long dirFileSize = dirINode.getFileSize();

        // 开始递归删除目录中的内容
        recursionDeleteDir(dirINode);

        // 删除目标目录
        deleteDir(dirINodeIndex, dirINode);

        // 删除目标目录在父目录 FCB 中的信息
        fcbList[superFCBIndex] = null;

        // 更新路径上的文件夹大小
        updateFileSize(absolutePath, -dirFileSize);

        return new ServiceResult(Status.SUCCESS, "操作成功");
    }

    /**
     * 为文件名添加 copy 后缀
     * @param fileName 文件名
     * @return 添加了copy后缀的文件名
     */
    public String addFileNameSuffix(String fileName) {
        Random random = new Random();
        int lastIndex = fileName.lastIndexOf('.');
        // 如果没有后缀名
        if (lastIndex == -1) {
            return fileName + "_copy(" + random.nextInt(100000) +")";
        }
        else {
            return fileName.substring(0, lastIndex) + "_copy(" + random.nextInt(100000) +")" + fileName.substring(lastIndex);
        }
    }

    /**
     * 将外部文件拷贝到内部
     *      暂时只支持 绝对路径的外部文件
     * @param externalFilePath 外部文件的路径
     * @param internalDirPath 内部文件夹的路径
     */
    public ServiceResult copyFromOutToIn(String externalFilePath, String internalDirPath) {

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        // 定义一个输出流，类似 StringBuffer，根据读取数据的大小调整 byte 数组的大小
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            fis = new FileInputStream(externalFilePath);
            bis = new BufferedInputStream(fis);

            int len;
            byte[] buf = new byte[1024];

            // bis.read() 返回的是读取的有效字节的个数
            while ((len = bis.read(buf)) != -1) {
                // 有数据的话就将数据添加到输出流
                bos.write(buf, 0, len);
            }

            // 将输出流中的数据，转换成字符串
            String data = bos.toString();

            String fileName = addFileNameSuffix(getExternalFileName(externalFilePath));
            String targetFilePath;
            // 如果目标路径为 . （当前目录）
            if (".".equals(internalDirPath)) {
                targetFilePath = info.getNowPath() + "/" + fileName;
            }
            else
                targetFilePath = internalDirPath + "/" + fileName;

            // 创建目标文件
            ServiceResult serviceResult = makeDirOrFile(targetFilePath, false);
            // 如果创建目标文件不成功，则返回创建目标文件时的错误
            if (serviceResult.status != Status.SUCCESS){
                return serviceResult;
            }

            // 向目标文件输入内容
            return echoToFile(targetFilePath, data);

        } catch (FileNotFoundException e) {
            // 打开文件有误，则直接返回报错状态码
            return new ServiceResult(Status.EXTERNAL_FILE_OPEN_ERROR, "外部文件打开失败");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // 关闭所有流
            try {
                if (bis != null) {
                    bis.close();
                }
                if (fis != null) {
                    fis.close();
                }
                bos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 将内部文件拷贝到外部文件夹
     *      1、暂时只支持 外部目录的绝对路径
     *      2、如果存在同名文件，则直接覆盖
     * @param internalFilePath 内部文件的路径
     * @param externalDirPath 外部文件夹的路径
     */
    public ServiceResult copyFromInToOut(String internalFilePath, String externalDirPath) {

        // 获取内部文件的内容
        ServiceResult serviceResult = catFile(internalFilePath);
        String data = serviceResult.getMsg();
        // 如果读取文件出错，则返回读取时的报错信息
        if (serviceResult.status != Status.SUCCESS) {
            return serviceResult;
        }
        // 获取内部文件的文件名
        String fileName = addFileNameSuffix(getFileName(internalFilePath));
        // 外部目录路径拼接文件名
        String externalFilePath = externalDirPath + "\\" + fileName;

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(externalFilePath);
            fos.write(data.getBytes());
            return new ServiceResult(Status.SUCCESS, "操作成功");
        } catch (FileNotFoundException e) {
            // 外部文件路径有误，则直接返回报错状态码
            return new ServiceResult(Status.EXTERNAL_FILE_OPEN_ERROR, "外部文件打开失败");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * 系统内部的文件拷贝
     * @param internalFilePath 内部文件的路径
     * @param internalDirPath 内部文件夹的路径
     */
    public ServiceResult copyFromInToIn(String internalFilePath, String internalDirPath) {
        // 源文件的内容
        ServiceResult serviceResult = catFile(internalFilePath);
        String data = serviceResult.getMsg();
        // 如果读取文件出错，则返回读取时的报错信息
        if (serviceResult.getStatus() != Status.SUCCESS) {
            return serviceResult;
        }
        // 获取文件名
        String fileName = addFileNameSuffix(getFileName(internalFilePath));

        String targetFilePath;
        // 如果目标路径为 . （当前目录）
        if (".".equals(internalDirPath)) {
            if (!"/".equals(info.getNowPath()))
                targetFilePath = info.getNowPath() + "/" + fileName;
            else
                targetFilePath = fileName;
        }
        else
            targetFilePath = internalDirPath + "/" + fileName;

        // 创建目标文件
        ServiceResult serviceResult1 = makeDirOrFile(targetFilePath, false);
        // 如果创建目标文件不成功，则返回错误 status
        if (serviceResult1.getStatus() != Status.SUCCESS){
            return serviceResult;
        }

        // 向目标文件输入内容
        return echoToFile(targetFilePath, data);
    }

    /**
     * 修改文件权限
     * @param newPermission 新的权限
     * @param path 文件路径
     * @return 状态码
     */
    public ServiceResult changeFileMode(String newPermission, String path) {
        // 不可修改根目录的权限
        if ("/".equals(path)) {
            return new ServiceResult(Status.PERMISSION_ERROR, "不可修改用户在根目录的权限");
        }
        FCBBlock temFCBBlock;
        String dirName;
        // 如果不含有 / ，那么 path 就是 文件名
        if (!path.contains("/")) {
            temFCBBlock = info.getNowFCBBlock();
            dirName = path;
        }
        else {
            // 通过（相对/绝对）文件夹路径获取（其各级父目录都要可执行）
            Result fcbResult = getFCBBlockByPath(getSuperDirPath(path), PermissionCode.CAN_EXECUTE);
            if (fcbResult.getStatus() == Status.PERMISSION_ERROR)
                return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您不能修改该文件的权限，因为父目录没有执行权限");
            // 没找到，说明路径错误
            if(fcbResult.getStatus() == Status.PATH_ERROR)
                return new ServiceResult(Status.PATH_ERROR, "路径有误");
            temFCBBlock = (FCBBlock) fcbResult.getData();
            // 获取文件名/文件夹名
            dirName = getFileName(path);
        }

        boolean isFind = false;
        INode fileINode = null;

        // 在目标文件的父目录中寻找该 fileName 的文件信息（inode）
        FCB[] fcbList = temFCBBlock.getFCBList();
        for (FCB fcb : fcbList) {
            // 寻找名称为 fileName 的文件
            if (fcb != null && fcb.getFileName().equals(dirName)) {
                fileINode = getINodeByINodeIndex(fcb.getINodeIndex());
                // 校验该用户是否有权限修改（只有拥有者才能修改）
                if (!info.getNowUser().getUsername().equals(fileINode.getPermissions().getOwnerName())) {
                    return new ServiceResult(Status.PERMISSION_ERROR, "权限不足, 您不能修改文件权限，因为您不是拥有者");
                }
                isFind = true;
                break;
            }
        }
        // 没找到该名称的文件
        if (!isFind) {
            return new ServiceResult(Status.FILE_NOT_EXIST_ERROR, "文件不存在");
        }

        fileINode.getPermissions().setOwnerPermission((int) newPermission.charAt(0));
        fileINode.getPermissions().setOtherPermission((int)newPermission.charAt(1));

        return new ServiceResult(Status.SUCCESS, "修改成功");
    }

    /**
     * 创建普通用户（ 根目录权限为 r-x ）
     * @param username 用户名
     * @return 响应消息
     */
    public ServiceResult createUser(String username) {
        // 只有管理员可以创建用户
        if (info.getNowUser().isRoot()) {
            userList.add(new User(username, username, 5, false, false));
            return new ServiceResult(Status.SUCCESS, "新普通用户创建成功");
        }
        return new ServiceResult(Status.PERMISSION_ERROR, "您不是管理员，不能创建新用户");
    }

    /**
     * 创建管理员（ 根目录权限为 rwx ）
     * @param username 用户名
     * @return 响应消息
     */
    public ServiceResult createAdmin(String username) {
        // 只有管理员可以创建用户
        if (info.getNowUser().isRoot()) {
            userList.add(new User(username, username, 7, true, false));
            return new ServiceResult(Status.SUCCESS, "新管理员用户创建成功");
        }
        return new ServiceResult(Status.PERMISSION_ERROR, "您不是管理员，不能创建新用户");
    }

    /**
     * 注销用户（改变用户状态属性）
     */
    public void logout() {
        for (User user : userList) {
            if (info.getNowUser().getUsername().equals(user.getUsername())) {
                user.setOnline(false);
                break;
            }
        }
    }
}
