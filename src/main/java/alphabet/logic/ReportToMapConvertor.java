package alphabet.logic;

import alphabet.enums.ReportTransactionStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.Map;

@Service
public class ReportToMapConvertor {

    private final Gson gson;

    public ReportToMapConvertor(Gson gson) {
        this.gson = gson;
    }

    public Map<String, ReportTransactionStatus> convert(String report) {
        return gson.fromJson(report, getAsTransactionResultListType());
    }

    private Type getAsTransactionResultListType() {
        return TypeToken.getParameterized(Map.class, String.class, ReportTransactionStatus.class).getType();
    }
}
