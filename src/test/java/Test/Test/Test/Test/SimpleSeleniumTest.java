package Test.Test.Test.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.testng.annotations.*;

import okhttp3.*;
import com.google.gson.*;

import java.util.concurrent.TimeUnit;

public class SimpleSeleniumTest {

    WebDriver driver;

    // ⭐ Put your Gemini API key here
    String API_KEY = "ENTER YOUR API KEY";

    @BeforeClass
    public void setUp() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions opt = new ChromeOptions();
        opt.addArguments("--remote-allow-origins=*");
        driver = new ChromeDriver(opt);
    }

    // ⭐ Gemini AI Call
    private JsonArray callGemini(String prompt) throws Exception {

        // FIX 1: Increase timeout (prevents SocketTimeoutException)
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build();

        // FIX 2: Correct request format for Gemini 2.5 Flash
        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", prompt);

        JsonArray parts = new JsonArray();
        parts.add(textObj);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject payload = new JsonObject();
        payload.add("contents", contents);

        RequestBody body = RequestBody.create(
                payload.toString(),
                MediaType.parse("application/json")
        );

        // FIX 3: Correct Gemini API endpoint
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY)
                .post(body)
                .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
            throw new RuntimeException("Gemini API Error: " + response.code() + " - " + response.message());
        }

        String resp = response.body().string();
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        // FIX 4: Safe extraction of AI response
        String aiText =
                json.getAsJsonArray("candidates")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();

        return convert(aiText);
    }

    // ⭐ Convert AI text → JSON steps
    private JsonArray convert(String text) {
        JsonArray steps = new JsonArray();

        for (String line : text.split("\n")) {
            if (!line.contains(":")) continue;

            String[] arr = line.split(":", 2);
            String action = arr[0].trim().toLowerCase();
            String value = arr[1].trim();

            JsonObject o = new JsonObject();

            switch (action) {

                case "open":
                    o.addProperty("action", "open");
                    o.addProperty("value", value);
                    break;

                case "type":
                    String[] p = value.split(" ", 2);
                    o.addProperty("action", "type");
                    o.addProperty("locator", p[0]);
                    o.addProperty("value", p[1]);
                    break;

                case "click":
                    o.addProperty("action", "click");
                    o.addProperty("locator", value);
                    break;
            }

            steps.add(o);
        }

        return steps;
    }

    // ⭐ Execute AI steps using Selenium
    private void execute(JsonArray steps) throws Exception {

        for (JsonElement e : steps) {
            JsonObject s = e.getAsJsonObject();
            String action = s.get("action").getAsString();

            switch (action) {

                case "open":
                    driver.get(s.get("value").getAsString());
                    driver.manage().window().maximize();
                    Thread.sleep(800);
                    break;

                case "type":
                    driver.findElement(By.id(s.get("locator").getAsString()))
                            .sendKeys(s.get("value").getAsString());
                    Thread.sleep(800);
                    break;

                case "click":
                    driver.findElement(By.id(s.get("locator").getAsString())).click();
                    Thread.sleep(800);
                    break;
            }
        }
    }

    // ⭐ TestNG Test
    @Test
    public void aiSeleniumTest() throws Exception {

        String prompt =
                "Generate Selenium steps strictly in this format:\n" +
                "open: https://www.saucedemo.com/\n" +
                "type: user-name standard_user\n" +
                "type: password secret_sauce\n" +
                "click: login-button";

        JsonArray steps = callGemini(prompt);

        System.out.println("AI Steps:");
        System.out.println(steps.toString());

        execute(steps);
    }

    @AfterClass
    public void tearDown() throws Exception {
        Thread.sleep(20000);
        driver.quit();
    }
}
