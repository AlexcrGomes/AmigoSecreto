package br.com.amigosecreto.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve um ProblemDetail direto na resposta, para os pontos que rodam fora do Spring MVC (filtros de
 * segurança: 401 e 403).
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String slug, String title, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", Problems.PREFIX + slug);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
