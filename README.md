THE JOPA GATEWAY SERVLET FOR ORACLE
PL/SQL WEB APPLICATIONS. HTTP-only access to all your web development resources.
THE CHALLENGE.
You are developing a web application which is based on an Oracle database. You want to use
PSP (PL/SQLô Server Pages) technology along with other server-side scripting technologies,
like JSP, to build the application and leverage the power of PL/SQL where appropriate. However,
your developers are working remotely and you donít want to open direct access to the
database for them or anyone else outside your local network. Dynamic PSPô seems to be natural
choice for these requirements, but your developers are unfamiliar with its web-based development
interface or find it too simple and limited compared to web development tools like Adobe
DreamWeaver or Microsoft FrontPage. You need some way to provide them with
access to the Dynamic PSP unit repository, the database and the web server without putting the
database at risk by opening it to the whole world and opening too many alternate access paths
to the web server file system. You also want your Dynamic PSP application to process requests
as quickly as possible with minimal overhead introduced by the middleware.

THE SOLUTION.
WebDAV (Web-based Distributed Authoring and Versioning) is a widely accepted standard for
remote read-write access to web resources and is supported by all major web development
tools. WebDAV makes remote resources look like local file systems for compatible clients and
allows for creating, copying, editing and deleting remote resources through the HTTP transport,
so you donít need to open any other ports and service other protocols on your server or allow
direct access to the database servers.
The JOPAô Gateway Servlet for Oracle PL/SQL Web Applications implements WebDAV access
to the Dynamic PSP unit repository as well as the server file systems and allows your developers
to use their favorite web authoring tools for creating and editing PSP units or any other files
on the web server in consistent and secure manner.
In addition to WebDAV support, the JOPA Gateway Servlet features an optimized Dynamic PSP
request processing pipeline, which is designed and built specifically to provide the best possible
DPSP throughput. In Native DPSP mode, the JOPA Gateway Servlet even supports progressive
output from your DPSP units, when the page output is streamed to the client as it is generated
by your DPSP application significantly reducing perceived and actual response time, unlike traditional
mode of execution, when the output is fully buffered on the server before being transmitted
to the client. The servlet can also compress the output from your Dynamic PSP and PL/SQL
applications on the fly, significantly reducing bandwidth requirements and page transfer and
load times.
And the Servlet can even replace Oracleís mod_plsql PL/SQL gateway module completely! In
OWA compatibility mode, the Servlet works pretty much identically to the mod_plsql, with some
serious limitations of the latter removed along with the gateway module maintenance burden,
thanks to the fact that the JOPA Gateway Servlet is 100% Javaô and runs everywhere thereís
Java2 Runtime Environment 1.4 or later and Java Servlet 2.0-compatible container. You can deploy
the same binary on any platform and application server where Java Servlet 2.0 specification
is supported and rest assured that it will run without any modifications, and new releases
or patches will be the same binary for all platforms either. You can be sure the output your Dynamic
PSP and PL/SQL applications generate is streamed to the client unmodified unlike when
mod_plsql is used, because mod_plsql reviews and alters your output in some cases, effectively 
prohibiting some advanced HTTP 1.1 protocol features. The Servlet also allows for efficient Oracle
connection pooling and reuse on all platforms thanks to native threading and common connection
pool. mod_plsql connection pooling on Unix platforms suffers from the OS process architecture
ñ each HTTP server process runs its own copy of the gateway module with its own
connection pool, preventing efficient connection reuse unless you use some non-trivial dual
HTTP server with internal proxying hacks (and even with these hacks itís not as efficient as on
operating systems with native threading, like Microsoft WindowsÆ.) The JOPA Servlet maintains
as much Oracle connections as the current workload requires, no more, no less, and reuses
them efficiently, on any operating system.
The JOPA Gateway Servlet is administered through the built-in web interface, so you never
need to edit its configuration file manually introducing the risk of breaking something due to
typing errors, and all changes made through the web administration interface take effect immediately,
without the need to restart the HTTP server.
The JOPA Gateway Servlet features extensive and highly configurable logging facility and can
log all aspects of its operation to the console, log file, or both for future review or troubleshooting
with variable configurable level of verbosity. By changing the logging detail level you can
easily drill down to any problem with the Servlet or your applications served through it. See
what comes in, how it is processed and what is sent back, and any errors on the way, all in single
log file.
SYSTEM REQUIREMENTS.
The following are system and software requirements for the JOPA Gateway Servlet:
- Java Run-time Environment (JRE) Version 1.4.0 or later (Version 1.4.2 or later is recommended)

- Compatible J2EE servlets container (Servlet Specification 2.0 or later support is required)
- Oracle8i Release 2 (8.1.6) or later database backend (Oracle9i Release 2 (9.2) or Oracle
Database 10g (10.1) with the latest patch set recommended)
- 256MB RAM on application server host (512MB or more recommended)
COPYRIGHT INFORMATION AND ACKNOWLEDGEMENTS
JOPA, DPSP, Dynamic PSP, PPSP, Procedural PSP, OPSP and Objective PSP are trademarks of OOO Hit-Media.
DPSP Interpreter, DPSP System Units and this document are Copyright© 2000-2005 by OOO Hit-Media.
JOPA Gateway Servlet is Copyright© 2002-2005 by OOO Hit-Media.
Oracle is the registered trademark of Oracle Corporation.
PL/SQL, Oracle8i, Oracle9i, Oracle Internet Server, Oracle WebServer and Oracle WebServer Option are trademarks of Oracle Corporation.

Sun, Sun Microsystems, the Sun Logo and Java are trademarks or registered trademarks of Sun Microsystems Inc. in United States
and other countries.
Other company or product names are mentioned for identification purposes only and may be service marks, trademarks, or registered
trademarks of their respective owners.
For more information, please mail us at info@dynamicpsp.com or visit our web site at http://www.dynamicpsp.com.
