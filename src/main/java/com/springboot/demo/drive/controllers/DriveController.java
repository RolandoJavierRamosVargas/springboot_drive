package com.springboot.demo.drive.controllers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
@CrossOrigin(origins = { "http://localhost:3000" })
@Controller
public class DriveController {

	private static final Logger logger = LoggerFactory.getLogger(DriveController.class);

	private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE,
			"https://www.googleapis.com/auth/drive.install");

	private static final String USER_IDENTIFIER_KEY = "MY_DUMMY_USER";

	@Value("${google.oauth.callback.uri}")
	private String CALLBACK_URI;

	@Value("${google.secret.key.path}")
	private Resource gdSecretKeys;

	@Value("${google.credentials.folder.path}")
	private Resource credentialsFolder;

	private GoogleAuthorizationCodeFlow flow;

	
	@PostConstruct
	public void init() throws Exception {
		GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(gdSecretKeys.getInputStream()));
		flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(credentialsFolder.getFile())).build();
	}

	@GetMapping(value = { "/" })
	public String showHomePage() throws Exception {
		boolean isUserAuthenticated = false;

		Credential credential = flow.loadCredential(USER_IDENTIFIER_KEY);
		if (credential != null) {
			boolean tokenValid = credential.refreshToken();
			
			if (tokenValid) {
				isUserAuthenticated = true;
			}
		}

		return isUserAuthenticated ? "dashboard.html" : "index.html";
	}

	@GetMapping(value = { "/googlesignin" })
	public void doGoogleSignIn(HttpServletResponse response) throws Exception {
		GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
		String redirectURL = url.setRedirectUri(CALLBACK_URI).setAccessType("offline").build();
		response.sendRedirect(redirectURL);
	}

	@GetMapping( "/oauth" )
	public ResponseEntity<?> saveAuthorizationCode(HttpServletRequest request) throws Exception {
		Map<String,Object> mapa= new HashMap<>();
		String code = request.getParameter("code");
		if (code != null) {
			saveToken(code);
			mapa.put("mensaje", "Se ha iniciado con exito");
			return new ResponseEntity<Map<String,Object>>(mapa,HttpStatus.OK);
//			return "dashboard.html";
		}
		mapa.put("mensaje", "Hubo un error");
		return new ResponseEntity<Map<String,Object>>(mapa,HttpStatus.NOT_FOUND);
//		return "index.html";
	}

	private void saveToken(String code) throws Exception {
		GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(CALLBACK_URI).execute();
		flow.createAndStoreCredential(response, USER_IDENTIFIER_KEY);

	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity uploadFile(@RequestParam MultipartFile file) throws Exception {

		Map<String,Object> resp=new HashMap<>();

		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
		boolean tokenValid = cred.refreshToken();

		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
				.setApplicationName("googledrivespringbootexample").build();

		Path ruta=coyFileIntoServidor(file);

		String result = searchFile(drive, "02.UPG.FISI.CARPETAS.ALUMNOS");
		if (result.equals("notFound")) {
			logger.info("Se tiene que crear la estrucura");
			// crearemos la estructura desde el inicio
		}
		logger.info("Ha regresado el id: " + result);
		// entonces solo crearemos la estructura desde upgifis
		// aca nuevamente tendremos que verificar si existe ese nombre
		String disi01 = searchFile(drive, "02.EPGA");
		if (disi01.equals("notFound")) {
			// crearemos el 01.disi
			disi01 = crearFolder("02.EPGA", drive, result);
		}
		String anio = searchFile(drive, "2015");
		if (anio.equals("notFound")) {
			// crearemos el anio 2018-1 dentro de ese folder
			anio = crearFolder("2015", drive, disi01);
		}
		// en este punto existe el folder 2018-1
		// entonces crearemos el folder que sigue
		String nombre = searchFile(drive, "CODIGO2-APEPATERNO2-APEMATERNO2-NOMBRES2");
		if (nombre.equals("notFound")) {
			// create el folder
			nombre = crearFolder("CODIGO2-APEPATERNO2-APEMATERNO2-NOMBRES2", drive, anio);
		}
		// en este punto ya tenemos creado el folder total
		// upload into folder
		String idFile=uploadIntoFolder(file, drive, nombre);
		
		
		//eliminar cualquier imagen puesta en el drive
		
		deleteFile(ruta);
		resp.put("mensaje","exito");
		resp.put("idFile", idFile);
		return new ResponseEntity<Map<String,Object>>(resp,HttpStatus.OK);
	}

	public String crearFolder(String name, Drive drive, String id) {
		File files = new File();
		files.setName(name);
		files.setMimeType("application/vnd.google-apps.folder");
		if (!id.equals("")) {
			files.setParents(Arrays.asList(id));
		}

		try {
			File rutaCarpeta = drive.files().create(files).setFields("id").execute();
			id = rutaCarpeta.getId();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return id;
	}

	public String searchFile(Drive driveService, String name) {
		String id = "";
		String pageToken = null;
		do {
			FileList result = null;
			try {
				result = driveService.files().list().setQ("name = '" + name + "'").setSpaces("drive")
						.setFields("nextPageToken, files(id, name,mimeType)").setPageToken(pageToken).execute();
			} catch (IOException e) {
				logger.info("No se encontro el archivo" + e.getMessage().concat(":").concat(e.getCause().toString()));
			}

			for (File file : result.getFiles()) {
				System.out.printf("Found file: %s (%s) (%s) \n", file.getName(), file.getId(),file.getMimeType());
				id = file.getId();
			}

			pageToken = result.getNextPageToken();
		} while (pageToken != null);

		if (id.equals("")) {
			return "notFound";
		} else {
			return id;
		}

	}

	public String uploadIntoFolder(MultipartFile file, Drive drive, String id) {
		String fileName = file.getOriginalFilename();
		Path rutaArchivo = Paths.get("uploads").resolve(fileName).toAbsolutePath();
		String contentType = file.getContentType();

		String ruta = rutaArchivo.toString();
		File fileMetadata = new File();
		fileMetadata.setName(fileName);
		fileMetadata.setParents(Arrays.asList(id));
		FileContent mediaContent = new FileContent(contentType, new java.io.File(ruta));
		File archivo = null;
		try {
			archivo = drive.files().create(fileMetadata, mediaContent).setFields("id").execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return archivo.getId();

	}
	
	public Path coyFileIntoServidor(MultipartFile file) {
		String fileName = file.getOriginalFilename();
		Path rutaArchivo = Paths.get("uploads").resolve(fileName).toAbsolutePath();
		String contentType = file.getContentType();	
		try {
			Files.copy(file.getInputStream(), rutaArchivo);
		} catch (IOException e) {
			
		}
		return rutaArchivo;
	}
	
	public void deleteFile(Path rutaArchivo) {
		java.io.File archivoFotoAnterior = rutaArchivo.toFile();	
		archivoFotoAnterior.delete();
	}
	
	public void deleteFile(String fileId,Drive drive) throws Exception {
		
		drive.files().delete(fileId).execute();
		logger.info("the file has been deleted");
	}
	
	@DeleteMapping(value = { "/deletefile/{fileId}" }, produces = "application/json")
	public ResponseEntity<?> deleteFile(@PathVariable(name = "fileId") String fileId) {
		Map<String,Object> resp= new HashMap<>();
		Credential cred;
		try {
			cred = flow.loadCredential(USER_IDENTIFIER_KEY);
			Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
					.setApplicationName("googledrivespringbootexample").build();
			
			drive.files().delete(fileId).execute();
		} catch (IOException e) {
			resp.put("mensaje", "hubo un error, no se pudo eliminar");
			return new ResponseEntity<Map<String,Object>>(resp,HttpStatus.INTERNAL_SERVER_ERROR);
		}

		
		resp.put("mensaje", "eliminado exitoso");

		return new ResponseEntity<Map<String,Object>>(resp,HttpStatus.OK);
	}
	
	///Para un Futuro  , espero alguien logre desarrollarlo 
//	@GetMapping("/download/{id}")
//	public ResponseEntity<?> dowloadFile(@PathVariable String id) {
//	
//		Credential cred=null;
//		try {
//			cred = flow.loadCredential(USER_IDENTIFIER_KEY);
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		
//		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
//				.setApplicationName("googledrivespringbootexample").build();
//		
//		Map<String,Object> resp=new HashMap<>();
//		
//		String type=searchTypeFile(drive,id);
//		if(type.equals("notFoundType")){
//			resp.put("error", "No se encontro el archivo con ese id");
//			return new ResponseEntity<Map<String,Object>>(resp,HttpStatus.BAD_REQUEST);
//		}
//		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//		try {
//			drive.files().get(id).executeMediaAndDownloadTo(outputStream);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		ByteArrayInputStream stream =  new ByteArrayInputStream(outputStream.toByteArray());
//		String name="a";
//		InputStreamResource input = new InputStreamResource(stream);
//		File files = new File();
//		files.setName("documento");
//		files.setMimeType(type);
//		System.out.println("El nombre"+files.getOriginalFilename());
//		System.out.println("33");
//		
//		System.out.println("name"+name);
//		HttpHeaders headers = new HttpHeaders();
//		headers.add("Content-Disposition", "attachment; filename=\""+name+"\"");
//		
////		return ResponseEntity.ok().headers(headers).body(input);
//		return new ResponseEntity<String>("hola",HttpStatus.OK);
//	}
	
	public String searchTypeFile(Drive driveService, String identifi) {
		String id = identifi;
		String pageToken = null;
		do {
			FileList result = null;
			try {
				result = driveService.files().list().setFields("files(id,mimeType)").execute();
			} catch (IOException e) {
//				logger.info("No se encontro el archivo" + e.getMessage().concat(":").concat(e.getCause().toString()));
			}

			for (File file : result.getFiles()) {
				if(id.equals(file.getId())) {
					
					System.out.printf("Found file Type: %s (%s) \n", file.getId(),file.getMimeType());
					return file.getMimeType(); 
				}
			}

			pageToken = result.getNextPageToken();
		} while (pageToken != null);

		return "notFoundType";

	}
}
