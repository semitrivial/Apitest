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
  String href_base = "";
  String href_postfix = "";
  String prev_result = null;
  String prev_href = null;
  boolean prev_result_unreadable = false;
  int linenum = 0;
  int sleeptime = 100;
  int total_queries = 0;
  int nonproblematic_queries = 0;

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

    outln( "Finished running " + apitest.total_queries + " queries." );

    if ( apitest.nonproblematic_queries < apitest.total_queries )
      outln( "Of those, " + (apitest.total_queries - apitest.nonproblematic_queries) + " had unexpected behavior." );
    else
      outln( "All queries behaved as expected." );

  }

  public Apitest( String [] args ) throws BadCommandLineException
  {
    if ( !this.parse_cmdline( args ) )
      throw new BadCommandLineException();
  }

  public Apitest( String filename ) throws BadCommandLineException
  {
    this.filename = filename;

    if ( !this.open_file() )
      throw new BadCommandLineException();
  }

  private void run()
  {
    for ( String line = read_line(); line != null; line = read_line() )
    {
      if ( !parse_line( line ) )
        return;
    }
  }

  private boolean parse_line( String line )
  {
    line = line.trim();
    linenum++;

    if ( "".equals(line) || line.charAt(0) == '#' )
      return true;

    if ( line.startsWith( "Sleeptime " ) )
    {
      sleeptime = java.lang.Integer.valueOf( after(line, "Sleeptime") );

      if ( sleeptime < 0 )
      {
        parser_error( "Sleeptime must be a non-negative integer" );
        return false;
      }

      return true;
    }

    if ( line.startsWith( "Include " ) )
    {
      Apitest apitest;

      try
      {
        apitest = new Apitest( after(line, "Include") );
      }
      catch( BadCommandLineException e )
      {
        parser_error( "Could not 'Include' the indicated file" );
        return false;
      }

      sync_apitests(apitest,this);
      apitest.run();
      sync_apitests(this,apitest);

      total_queries += apitest.total_queries;
      nonproblematic_queries += apitest.nonproblematic_queries;

      return true;
    }

    if ( line.startsWith( "Base " ) )
    {
      href_base = after(line, "Base");

      if ( href_base.equals("none") )
        href_base = "";

      return true;
    }

    if ( line.startsWith( "Postfix " ) )
    {
      href_postfix = after(line, "Postfix");

      if ( href_postfix.equals("none") )
        href_postfix = "";

      return true;
    }

    if ( line.startsWith( "Expect " ) )
    {
      if ( prev_result == null )
      {
        parser_error( "'Expect' found before any requests were found" );
        return false;
      }

      if ( prev_result_unreadable )
        return true;

      String expected = after(line, "Expect");

      if ( !prev_result.contains( expected ) )
      {
        outln( "("+filename+":"+linenum+") URL: " + prev_href );
        outln( "Expected pattern was not found in the API response!" );
        outln( "" );
        prev_result_unreadable = true;
        nonproblematic_queries--;
      }

      return true;
    }

    return send_api_query( line );
  }

  private boolean send_api_query( String q )
  {
    String href = href_base + q + href_postfix;
    URL url;
    URLConnection con;

    total_queries++;

    try
    {
      url = new URL( href );
    }
    catch( MalformedURLException e )
    {
      parser_error( "This URL does not seem to be well-formed", e );
      return false;
    }

    prev_href = href;

    try
    {
      con = url.openConnection();
    }
    catch( IOException e )
    {
      parser_error( "Failed to open connection", e );
      prev_result = "";
      prev_result_unreadable = true;
      return true;
    }

    try
    {
      BufferedReader r = new BufferedReader( new InputStreamReader( con.getInputStream() ) );
      StringBuilder sb = new StringBuilder();

      for ( String replyline = read_line( r ); replyline != null; replyline = read_line( r ) )
      {
        sb.append( replyline );
        sb.append( "\n" );
      }

      prev_result = sb.toString();
      prev_result_unreadable = false;
    }
    catch( IOException e )
    {
      outln( "("+filename+":"+linenum+") URL: " + href );
      outln( "Failed to read API results due to Input/Output Exception" );
      outln( "Details: "+e.getLocalizedMessage() );
      outln( "" );
      prev_result = "";
      prev_result_unreadable = true;
      return true;
    }

    try
    {
      Thread.currentThread().sleep( sleeptime );
    }
    catch( java.lang.InterruptedException e )
    {
      parser_error( "Failed to sleep between API requests", e );
      return false;
    }

    nonproblematic_queries++;
    prev_result_unreadable = false;
    return true;
  }

  private String read_line( )
  {
    return read_line( br );
  }

  private String read_line( BufferedReader bufferedReader )
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

  private boolean parse_cmdline( String [] args )
  {
    if ( args.length < 1 )
    {
      outln( "Syntax: java Apitest [optional arguments] file" );
      outln( "(where file is an Apitest file, with info about what commands to send to what host)" );

      return false;
    }

    filename = args[args.length-1];

    return open_file();
  }

  private static void outln( String str )
  {
    System.out.println( str );
  }

  private void errln( String err )
  {
    System.err.println( err );
  }

  private void parser_error( String err )
  {
    if ( linenum != 0 )
      errln( "(" + filename + ":" + linenum + ") " + err );
    else
      errln( "(" + filename + ") " + err );
  }

  private void parser_error( String err, Throwable e )
  {
    parser_error( err + "\nDetails: " + e.getLocalizedMessage() );
  }

  boolean open_file()
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

  void sync_apitests( Apitest dest, Apitest src )
  {
    dest.href_base = src.href_base;
    dest.href_postfix = src.href_postfix;
    dest.prev_href = src.prev_href;
    dest.prev_result_unreadable = src.prev_result_unreadable;
    dest.sleeptime = src.sleeptime;
  }

  private static String after(String line, String start)
  {
    return line.substring(start.length() + 1).trim();
  }
}
