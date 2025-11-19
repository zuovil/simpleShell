import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DealProcessStream extends Thread {

    private final InputStream inputStream;

    public DealProcessStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void run() {

        try{
            // Process 的管道是敏感资源，在子线程关闭会打乱 Process 的正常生命周期
            // 因此不能在这里关闭输入流，在这里关闭会导致Process甚至System.in输入流被提前关闭，影响后续进程（Process比较特殊）
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }

}
