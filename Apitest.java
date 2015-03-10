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
  InputStream is;
  BufferedReader br;
  String line;
  String base_href;
  String prev_result;
  String prev_href;
  boolean prev_result_unreadable;
  int linenum;
  int sleeptime;
  int total_queries;
  int nonproblematic_queries;

  public static void main(String [] args) throws Exception
  {
    Apitest apitest = new Apitest();
    apitest.run( args );
  }

  private void run( String [] args )
  {
    if ( !parse_cmdline( args ) )
      return;

    linenum = 0;
    base_href = "";
    prev_result = null;
    prev_result_unreadable = false;
    total_queries = 0;
    nonproblematic_queries = 0;

    for ( String line = read_line(); line != null; line = read_line() )
    {
      if ( !parse_line( line ) )
        return;
    }

    outln( "Finished running " + total_queries + " queries." );

    if ( nonproblematic_queries < total_queries )
      outln( "Of those, " + (total_queries - nonproblematic_queries) + " had unexpected behavior." );
    else
      outln( "All queries behaved as expected." );
  }

  private boolean parse_line( String line )
  {
    line = line.trim();
    linenum++;

    if ( "".equals(line) || line.charAt(0) == '#' )
      return true;

    if ( line.startsWith( "Base " ) )
    {
      base_href = line.substring( "Base ".length() ).trim();

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

      String expected = line.substring( "Expect ".length() );

      if ( !prev_result.contains( expected ) )
      {
        outln( "(Line "+linenum+") URL: " + prev_href );
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
    String href = base_href + q;
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
      return false;
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
      outln( "(Line "+linenum+") URL: " + href );
      outln( "Failed to read API results due to Input/Output Exception" );
      outln( "Details: "+e.getLocalizedMessage() );
      outln( "" );
      prev_result = "";
      prev_result_unreadable = true;
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
    sleeptime = 100;

    if ( args.length < 1 )
    {
      outln( "Syntax: java Apitest [optional arguments] file" );
      outln( "(where file is an Apitest file, with info about what commands to send to what host)" );

      return false;
    }

    String filename = args[args.length-1];

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

  private void outln( String str )
  {
    System.out.println( str );
  }

  private void errln( String err )
  {
    System.err.println( err );
  }

  private void parser_error( String err )
  {
    errln( "(Line " + linenum + ") " + err );
  }

  private void parser_error( String err, Throwable e )
  {
    parser_error( err + "\nDetails: " + e.getLocalizedMessage() );
  }
}
