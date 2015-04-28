import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BufferedReadersTry1 
{
	// User Input - Start
	private final Path searchDirectory = Paths.get("D:\\DevTools");
	private final String searchTxt = "eclipse";
	// User Input - End


	private final long threshold = 10 * 1024 * 1024;
	ExecutorService es = Executors.newFixedThreadPool(5);
	Pattern pattern = Pattern.compile(Pattern.quote(searchTxt), Pattern.CASE_INSENSITIVE);
	List<FutureHolderDTO> futureHolderDTOLst = new ArrayList<FutureHolderDTO>();
	BufferedWriter bw;
	PrintWriter pw;
	BufferedWriter rw;

	private class FutureHolderDTO 
	{
		private Path filePath;
		private Future<?> future;

		public Path getFilePath() {
			return filePath;
		}
		public void setFilePath(Path filePath) {
			this.filePath = filePath;
		}
		public Future<?> getFuture() {
			return future;
		}
		public void setFuture(Future<?> future) {
			this.future = future;
		}
	}

	private class MyFileVistor extends SimpleFileVisitor<Path>
	{
		@Override
		public FileVisitResult visitFile(final Path path, BasicFileAttributes attrs) throws IOException 
		{
			if(path.toFile().length() <= threshold){
				try{
					searchSmallFile(path);
				}catch(IOException e){
					e.printStackTrace(pw);
				}
			}else{
				Future<?> future = es.submit(new Runnable() {
					@Override
					public void run() {
						try {
							searchLargeFile(path);
						} catch (IOException e) {
							e.printStackTrace(pw);
						}
					}
				});
				FutureHolderDTO futureHolderDTO = new FutureHolderDTO();
				futureHolderDTO.setFilePath(path);
				futureHolderDTO.setFuture(future);
				futureHolderDTOLst.add(futureHolderDTO);
			}
			return super.visitFile(path, attrs);
		}
	}

	private void searchSmallFile(final Path path) throws IOException
	{
		log("Started Searching "+ path);
		String str = new String(Files.readAllBytes(path));
		Matcher patternMatcher = pattern.matcher(str);
		if(patternMatcher.find()){
			rw.write(path.toString());
			rw.newLine();
		}
		log("Finished Searching "+ path);
	}

	private void log(String str)
	{
		try {
			long time = System.currentTimeMillis();
			bw.write(time + str);
			bw.newLine();
		} catch (IOException e) {
			e.printStackTrace(pw);
		}
	}

	private void searchLargeFile(final Path path) throws IOException
	{
		log("Started Searching "+ path);
		int bufferSize = 10 * 1024 * 1024;
		try (FileInputStream fs = new FileInputStream(path.toFile());
				FileChannel in = fs.getChannel()){
			byte[] byteArr = new byte[bufferSize];
			ByteBuffer bytebuf = ByteBuffer.wrap(byteArr);
			int bytesCount = 0;
			String prependStr = "";
			while ((bytesCount = in.read(bytebuf)) > 0) {
				String pageStr = prependStr + new String(byteArr, 0, bytesCount);
				Matcher patternMatcher = pattern.matcher(pageStr);
				if(patternMatcher.find()){
					rw.write(path.toString());
					rw.newLine();
					break;
				}
				// Check and handle 'cut' lines - Start
				int i = pageStr.length() - 1;
				int newLinePos = i; 
				for(; i >= 0; i--){
					char c = pageStr.charAt(i);
					if(c == '\r' || c == '\n' ){
						newLinePos = i;
						break;
					}
				}
				if((newLinePos + 1) < pageStr.length()){
					prependStr = pageStr.substring(newLinePos + 1);
				}else{
					prependStr = "";
				}
				// Check and handle 'cut' lines - End
				bytebuf.clear();
			}
		}
	}

	public void go()
	{
		try {
			long fromTime = System.currentTimeMillis();
			bw = new BufferedWriter(new FileWriter("log.txt"));
			pw = new PrintWriter(bw);
			rw = new BufferedWriter(new FileWriter("output.txt"));
			Files.walkFileTree(searchDirectory, new MyFileVistor());
			for(FutureHolderDTO futureHolderDTO:futureHolderDTOLst){
				try {
					log("Still Searching " + futureHolderDTO.getFilePath());
					futureHolderDTO.getFuture().get();
					log("Finished Searching " + futureHolderDTO.getFilePath());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace(pw);
				}
			}
			es.shutdown();
			bw.close();
			long toTime = System.currentTimeMillis();
			log("Total Time taken(in seconds): " + ((toTime - fromTime)/1000d));
		} catch (IOException e) {
			e.printStackTrace(pw);
		}
	}

	public static void main(String[] args) 
	{
		new BufferedReadersTry1().go();
		//313 349 141 // 114517 ie 1.909 minutes 77109,  233,756 1.285 minutes 3.896 minutes 316234 316234 5.271 minutes 555995 9.267 minutes
	}
}
