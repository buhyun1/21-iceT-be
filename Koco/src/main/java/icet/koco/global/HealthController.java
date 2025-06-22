package icet.koco.global;

import icet.koco.enums.ApiResponseCode;
import icet.koco.global.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.success(ApiResponseCode.SUCCESS, "헬스 체크 정상", "OK")
        );
    }
}
