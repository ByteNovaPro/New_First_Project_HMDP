public class Execise {
    public static void main(String[] args) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("传统方式创建线程");
            }
        }).start();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("传统方式创建线程2");
            }
        };
        new Thread(runnable).start();
    }
}
