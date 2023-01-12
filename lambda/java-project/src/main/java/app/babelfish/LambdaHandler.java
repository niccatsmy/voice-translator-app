package app.babelfish;


import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.services.polly.model.VoiceId;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.translate.AmazonTranslateClient;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import com.amazonaws.transcribestreaming.TranscribeStreamingClientWrapper;
import com.amazonaws.transcribestreaming.TranscribeStreamingSynchronousClient;

import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;


public class LambdaHandler implements RequestHandler<Input, String> {
	
	AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();
	AmazonTranslate translate = AmazonTranslateClient.builder().build();
    AmazonPolly polly = AmazonPollyClientBuilder.defaultClient();
	AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();
    
    
	@Override
	public String handleRequest(Input name, Context context) {
		
		LambdaLogger logger = context.getLogger();
		
		logger.log("Bucket: " + name.getBucket());
		logger.log("Key: " + name.getKey());
		logger.log("Source Language: " + name.getSourceLanguage());
		logger.log("Target: " + name.getTargetLanguage());
		
		// Converting Audio to Text using Amazon Transcribe service.
        String transcript = transcribe(logger, name.getBucket(), name.getKey(), name.getSourceLanguage());

		// Create temporary file as input for comprehend
		String outputFileName = "/tmp/output.txt";

		try {
			File outputFile = new File("/tmp/output.txt"); 
			FileWriter writer = new FileWriter(outputFile);
			writer.write(transcript);
			writer.close();
			System.out.println("Wrote content to file");
		} catch (Exception e) {
			System.out.println("Unable to write out file");
		}

		// Getting sentiment score from Amazon Comprehend service
		InvokeRequest invokeRequest = new InvokeRequest();
		invokeRequest.setFunctionName("sentimentAnalysis");
		InvokeResult sentiment = lambda.invoke(invokeRequest);

		String ans = new String(sentiment.getPayload().array(), StandardCharsets.UTF_8);

		System.out.println(ans);

		//Translating text from one language to another using Amazon Translate service.
		String translatedText = translate(logger, transcript, name.getSourceLanguage(), name.getTargetLanguage());
		
		
		// Save file to s3 bucket

        // String fileName = saveOnS3(name.getBucket(), outputFileName);
    	
		return transcript;
	}
	
	private String saveOnS3(String bucket, String outputFile) {

		String fileName = "output/" + new Date().getTime() + ".txt";
		
		PutObjectRequest request = new PutObjectRequest(bucket, fileName, new File(outputFile));
		s3.putObject(request);
		
		return fileName;
		
	}

	private String transcribe(LambdaLogger logger, String bucket, String key, String sourceLanguage) {
		
		LanguageCode languageCode = LanguageCode.EN_US;
		
		if ( sourceLanguage.equals("es") ) {
			languageCode = LanguageCode.ES_US;
		}
		
		if ( sourceLanguage.equals("gb") ) {
			languageCode = LanguageCode.EN_GB;
		}
		
		if ( sourceLanguage.equals("ca") ) {
			languageCode = LanguageCode.FR_CA;
		}
		
		if ( sourceLanguage.equals("fr") ) {
			languageCode = LanguageCode.FR_FR;
		}
		
		
		File inputFile = new File("/tmp/input.wav");
		
    	s3.getObject(new GetObjectRequest(bucket, key), inputFile);

        TranscribeStreamingSynchronousClient synchronousClient = new TranscribeStreamingSynchronousClient(TranscribeStreamingClientWrapper.getClient());
        String transcript = synchronousClient.transcribeFile(languageCode, inputFile);
     
        logger.log("Transcript: " + transcript);
 
        return transcript;

	}	private String translate(LambdaLogger logger, String text, String sourceLanguage, String targetLanguage) {
		
		if (targetLanguage.equals("ca")) {
			targetLanguage = "fr";
		}
		
		if (targetLanguage.equals("gb")) {
			targetLanguage = "en";
		}
		
		TranslateTextRequest request = new TranslateTextRequest().withText(text)
                .withSourceLanguageCode(sourceLanguage)
                .withTargetLanguageCode(targetLanguage);
        TranslateTextResult result  = translate.translateText(request);
        
        String translatedText = result.getTranslatedText();
        
        logger.log("Translation: " + translatedText);
        
        return translatedText;
		
	}
}

class Input {
	private String bucket;
	private String key;
	private String sourceLanguage;
	private String targetLanguage;
	public String getBucket() {
		return bucket;
	}
	public void setBucket(String bucket) {
		this.bucket = bucket;
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getSourceLanguage() {
		return sourceLanguage;
	}
	public void setSourceLanguage(String sourceLanguage) {
		this.sourceLanguage = sourceLanguage;
	}
	public String getTargetLanguage() {
		return targetLanguage;
	}
	public void setTargetLanguage(String targetLanguage) {
		this.targetLanguage = targetLanguage;
	}
	
	
	
	
	
}