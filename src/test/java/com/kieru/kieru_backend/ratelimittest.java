//import com.kieru.backend.controller.SecretController;
//import com.kieru.backend.exception.RateLimitExceededException;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//@SpringBootTest
//class RateLimitTest {
//
//    @Autowired
//    private SecretController controller;
//
//    @Test
//    void testRateLimitExceeded() {
//        // Make 11 requests (limit is 10)
//        for (int i = 0; i < 11; i++) {
//            try {
//                controller.createSecret(...);
//            } catch (RateLimitExceededException e) {
//                assertEquals(11, i + 1); // Should fail on 11th request
//                return;
//            }
//        }
//        fail("Rate limit was not enforced");
//    }
//}