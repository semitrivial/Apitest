import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.lang.StringBuilder;

public class Apitest
{
  String filename;
  InputStream is;
  BufferedReader br;
  String line;
  String hrefBase = "";
  String hrefPostfix = "";
  String prevResult = null;
  String prevHref = null;
  boolean prevResultUnreadable = false;
  int lineNumber = 0;
  int sleepTime = 100;
  int totalQueries = 0;
  int nonproblematicQueries = 0;
  boolean codeFile = false;

  public static void main(String [] args) throws Exception
  {
    Apitest apitest;
    try
    {
      apitest = new Apitest( args );
    }
    catch( BadCommandLineException e )
    {
      return;
    }

    outln( "Running API commands..." );
    outln( "" );

    apitest.run();

    outln( "Finished running " + apitest.totalQueries + " queries." );

    if ( apitest.nonproblematicQueries < apitest.totalQueries )
      outln( "Of those, " + (apitest.totalQueries - apitest.nonproblematicQueries) + " had unexpected behavior." );
    else
      outln( "All queries behaved as expected." );

  }

  public Apitest( String [] args ) throws BadCommandLineException
  {
    if ( !this.parseCommandLine( args ) )
      throw new BadCommandLineException();
  }

  public Apitest( String filename, boolean codeFile ) throws BadCommandLineException
  {
    this.filename = filename;
    this.codeFile = codeFile;

    if ( !this.openFile() )
      throw new BadCommandLineException();
  }

  private void run()
  {
    for ( String line = readLine(); line != null; line = readLine() )
    {
      if ( !parseLine( line ) )
        return;
    }
  }

  private boolean parseLine( String line )
  {
    line = line.trim();
    lineNumber++;

    if ( codeFile )
    {
      while ( line.endsWith(";") )
        line = line.substring(0,line.length()-1).trim();

      if ( line.startsWith( "Apitest(" ) && line.endsWith( ")" ) )
      {
        line = line.substring( "Apitest(".length(), line.length()-1 );
        line = line.trim();

        if ( line.startsWith( "\"" ) && line.endsWith( "\"" ) )
        {
          line = line.substring( 1, line.length()-1 ).trim();
          line = decodeString( line );

          if ( line == null )
          {
            parserError( "Badly escaped C-like string, or C-like string with unsupported escape codes" );
            return false;
          }
        }
      }
      else
        return true;
    }

    if ( "".equals(line) || line.charAt(0) == '#' )
      return true;

    if ( line.startsWith( "sleeptime " ) )
    {
      sleepTime = java.lang.Integer.valueOf( after(line, "Sleeptime") );

      if ( sleepTime < 0 )
      {
        parserError( "Sleeptime must be a non-negative integer" );
        return false;
      }

      return true;
    }

    if ( line.startsWith( "Include " ) || line.startsWith( "IncludeCode " ) )
    {
      Apitest apitest;
      String starts;
      boolean isCode;

      if ( line.startsWith( "Include " ) )
      {
        starts = "Include";
        isCode = false;
      }
      else
      {
        starts = "IncludeCode";
        isCode = true;
      }

      try
      {
        apitest = new Apitest( after(line, starts), isCode );
      }
      catch( BadCommandLineException e )
      {
        parserError( "Could not 'Include' the indicated file" );
        return false;
      }

      syncApitests(apitest,this);
      apitest.run();
      syncApitests(this,apitest);

      totalQueries += apitest.totalQueries;
      nonproblematicQueries += apitest.nonproblematicQueries;

      return true;
    }

    if ( line.startsWith( "Base " ) )
    {
      hrefBase = after(line, "Base");

      if ( hrefBase.equals("none") )
        hrefBase = "";

      return true;
    }

    if ( line.startsWith( "Postfix " ) )
    {
      hrefPostfix = after(line, "Postfix");

      if ( hrefPostfix.equals("none") )
        hrefPostfix = "";

      return true;
    }

    if ( line.startsWith( "Expect " ) )
    {
      if ( prevResult == null )
      {
        parserError( "'Expect' found before any requests were found" );
        return false;
      }

      if ( prevResultUnreadable )
        return true;

      String expected = after(line, "Expect");

      if ( !prevResult.contains( expected ) )
      {
        outln( "("+filename+":"+lineNumber+") URL: " + prevHref );
        outln( "Expected pattern was not found in the API response!" );
        outln( "" );
        prevResultUnreadable = true;
        nonproblematicQueries--;
      }

      return true;
    }

    return sendApiQuery( line );
  }

  private boolean sendApiQuery( String q )
  {
    String href = hrefBase + q + hrefPostfix;
    URL url;
    URLConnection con;

    totalQueries++;

    try
    {
      url = new URL( href );
    }
    catch( MalformedURLException e )
    {
      parserError( "This URL does not seem to be well-formed", e );
      return false;
    }

    prevHref = href;

    try
    {
      con = url.openConnection();
    }
    catch( IOException e )
    {
      parserError( "Failed to open connection", e );
      prevResult = "";
      prevResultUnreadable = true;
      return true;
    }

    try
    {
      BufferedReader r = new BufferedReader( new InputStreamReader( con.getInputStream() ) );
      StringBuilder sb = new StringBuilder();

      for ( String replyLine = readLine( r ); replyLine != null; replyLine = readLine( r ) )
      {
        sb.append( replyLine );
        sb.append( "\n" );
      }

      prevResult = sb.toString();
      prevResultUnreadable = false;
    }
    catch( IOException e )
    {
      outln( "("+filename+":"+lineNumber+") URL: " + href );
      outln( "Failed to read API results due to Input/Output Exception" );
      outln( "Details: "+e.getLocalizedMessage() );
      outln( "" );
      prevResult = "";
      prevResultUnreadable = true;
      return true;
    }

    try
    {
      Thread.currentThread().sleep( sleepTime );
    }
    catch( java.lang.InterruptedException e )
    {
      parserError( "Failed to sleep between API requests", e );
      return false;
    }

    nonproblematicQueries++;
    prevResultUnreadable = false;
    return true;
  }

  private String readLine( )
  {
    return readLine( br );
  }

  private String readLine( BufferedReader bufferedReader )
  {
    try
    {
      return bufferedReader.readLine();
    }
    catch( IOException e )
    {
      return null;
    }
  }

  private boolean parseCommandLine( String [] args )
  {
    if ( args.length < 1 )
    {
      outln( "Syntax: java Apitest [optional arguments] file" );
      outln( "(where file is an Apitest file, with info about what commands to send to what host)" );

      return false;
    }

    filename = args[args.length-1];

    return openFile();
  }

  private static void outln( String str )
  {
    System.out.println( str );
  }

  private void errln( String err )
  {
    System.err.println( err );
  }

  private void parserError( String err )
  {
    if ( lineNumber != 0 )
      errln( "(" + filename + ":" + lineNumber + ") " + err );
    else
      errln( "(" + filename + ") " + err );
  }

  private void parserError( String err, Throwable e )
  {
    parserError( err + "\nDetails: " + e.getLocalizedMessage() );
  }

  boolean openFile()
  {
    try
    {
      is = new FileInputStream( filename );
    }
    catch( FileNotFoundException e )
    {
      errln( "File not found: " + filename );
      return false;
    }
    catch( SecurityException e )
    {
      errln( "Could not open file due to security exception: " + filename );
      return false;
    }

    InputStreamReader isr = new InputStreamReader( is );
    br = new BufferedReader( isr );

    return true;
  }

  class BadCommandLineException extends Exception
  {
    BadCommandLineException()
    {
    }
  }

  void syncApitests( Apitest dest, Apitest src )
  {
    dest.hrefBase = src.hrefBase;
    dest.hrefPostfix = src.hrefPostfix;
    dest.prevHref = src.prevHref;
    dest.prevResultUnreadable = src.prevResultUnreadable;
    dest.sleepTime = src.sleepTime;
  }

  private static String after(String line, String start)
  {
    return line.substring(start.length() + 1).trim();
  }

  String decodeString( String s )
  {
    StringBuilder sb = new StringBuilder( s.length() );
    int len = s.length();
    boolean fSlash = false;

    for ( int i = 0; i < len; i++ )
    {
      char c = s.charAt(i);

      if ( fSlash )
      {
        fSlash = false;
        switch( c )
        {
          case '\\':
            sb.append('\\');
            break;
          case '\"':
            sb.append('\"');
            break;
          default:
            return null;
        }
      }
      else
      {
        switch(c)
        {
          case '\\':
            fSlash = true;
            continue;
          case '\"':
            return null;
          default:
            sb.append(c);
        }
      }
    }

    if ( fSlash )
      return null;
    else
      return sb.toString();
  }
}
