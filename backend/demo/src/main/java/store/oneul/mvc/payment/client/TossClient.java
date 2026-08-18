package store.oneul.mvc.payment.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.payment.dto.PaymentConfirmRequest;
import store.oneul.mvc.payment.dto.TossCancelRequest;
import store.oneul.mvc.payment.dto.TossConfirmRequest;
import store.oneul.mvc.payment.dto.TossConfirmResponse;
import store.oneul.mvc.payment.dto.TossErrorInfo;
import store.oneul.mvc.payment.exception.PaymentConfirmException;

@Component
@RequiredArgsConstructor
public class TossClient {

    @Value("${toss.secret-key}")
    private String secretKey;

    private final RestClient tossRestClient;

    /**
     * ★ 측정용 스텁. payment.toss.stub=true 일 때만 빈이 존재한다 (기본 없음 = 실제 호출).
     * 보상 트랜잭션을 재려면 "결제 성공 + 내부 저장 실패"를 N건 만들어야 하는데
     * 실제 결제 승인은 카드 정보와 결제창이 필요해 부하로 만들 수 없다.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TossStub stub;

    public TossConfirmResponse confirm(PaymentConfirmRequest request) {
        if (stub != null) {
            return stub.confirm(request);
        }
        String encodedSecret = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        // Authorization: Basic {Base64.encode(secretKey + ":")}

        TossConfirmRequest tossRequest = new TossConfirmRequest(
                request.getOrderId(),
                request.getPaymentKey(),
                request.getAmount()
            );
        
        try {
            return tossRestClient.post()
                    .uri("/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(tossRequest)
                    .retrieve() // 응답 시작
                    .body(TossConfirmResponse.class); //응답 JSON → 객체로 역직렬화
        } catch (HttpClientErrorException e) {
            // TossErrorInfo 파싱 후 예외로 감싸서 던짐
            try {
                TossErrorInfo error = new ObjectMapper().readValue(e.getResponseBodyAsString(), TossErrorInfo.class);
                throw new PaymentConfirmException(error);
            } catch (Exception ex) {
                throw new RuntimeException("결제 실패 응답 파싱 오류", ex);
            }
        }
    }
    public void cancel(String paymentKey, TossCancelRequest request) {
        if (stub != null) {
            stub.cancel(paymentKey);
            return;
        }
        String encodedSecret = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            tossRestClient.post()
                .uri("/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity(); // Toss는 성공 시 response body 없음
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Toss Cancel 실패: " + e.getResponseBodyAsString(), e);
        }
    }
}
