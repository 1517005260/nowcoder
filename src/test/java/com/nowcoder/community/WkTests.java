package com.nowcoder.community;

import java.io.IOException;

public class WkTests {
    public static void main(String[] args) throws IOException {
        String cmd = "D:\\wkhtmltopdf\\bin\\wkhtmltoimage --quality 75 https://www.nowcoder.com D:\\wkhtmltopdf\\data\\imgs\\2.png";

        Runtime.getRuntime().exec(cmd);
        System.out.println("ok");
    }
}
