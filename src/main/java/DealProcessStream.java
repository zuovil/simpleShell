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

        InputStreamReader inputStreamReader = null;
        BufferedReader br = null;

        try {
            inputStreamReader = new InputStreamReader(inputStream);
            br = new BufferedReader(inputStreamReader);
            //打印信息
            String line = null;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }

        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

    }

}
