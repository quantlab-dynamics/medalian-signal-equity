package com.quantlab.common.emailService;

import com.quantlab.common.entity.Strategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public interface EmailService {

	public void sendEmail(String[] to, String subject, String body);
	public String getEmailOtpTemplate (String otp);
	public String getEmailFailedTemplate (String otp);
	public String getEmailErrorTemplate(String errorDetails);
	public String getEmailSuccessTemplate();
	public String getEmailCriticalErrorTemplate(String errorDetails);

	public String getEmailCriticalErrorBodTemplate(String errorDetails);
	public String getEmailSuccessBodTemplate(String message, String paragraph, List<Map<String, String>> tasks, String nextStep);
	public void sendAdminEmail(String errorDetails ,boolean status);
	public void sendStrategyErrorEmail(Strategy strategy, String errorMessage);
	public void sendEmailAlert(double ltp, long tradedTime);
	public void sendEmailAlertForNullData(String symbol);


}
