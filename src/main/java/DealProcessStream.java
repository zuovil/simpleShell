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

        try(BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))){
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }

}
