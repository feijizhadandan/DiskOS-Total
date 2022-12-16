package com.zhen.diskosweb.entity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class ResetOS {
    public static void main(String[] args) throws IOException {

        Disk disk = new Disk();
        disk.initDiskBlock();
        // 将对象输出到文件
        FileOutputStream os = new FileOutputStream("C:\\Users\\zengzhen\\Desktop\\diskOS.txt");
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(disk);
        oos.flush();
        oos.close();

    }
}
