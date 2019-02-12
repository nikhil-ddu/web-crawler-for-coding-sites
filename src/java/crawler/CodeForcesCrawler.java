/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import hibernate.HibernateUtil;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import model.Platform;
import model.Problem;
import model.SampleInputOutput;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 *
 * @author Nikhil
 */
public class CodeForcesCrawler implements Crawler {

    private static final int TAG_INDEX = 3;
    String BaseUrl = "http://www.codeforces.com/contests";
    String requestParameter = "complete=true";

    public CodeForcesCrawler(boolean restart) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void crawl() {

        HashSet<String> contestUrls = getAllContestUrls(BaseUrl + '?' + requestParameter);
        HashSet<String> problemUrls = getAllProblemUrls(contestUrls);

        for (String problemUrl : problemUrls) {
            Problem problem = scrapeProblemData(problemUrl);
            storeProblem(problem);
        }

    }

    private HashSet<String> getAllContestUrls(String url) {
        HashSet<String> contestsUrlSet = new HashSet<String>();
        try {
            Document doc = Jsoup.connect(url).get();
            Element pageContent = doc.getElementById("pageContent");
            Element contestsTable = pageContent.getElementsByClass("contests-table").first();
            Element datatable = contestsTable.getElementsByClass("datatable").first();
            Element table = datatable.getElementsByTag("table").first();
            Elements rows = table.getElementsByTag("tr");
            rows.remove(0);

            for (Element row : rows) {
                mysleep();
                Element link = row.getElementsByTag("a").first();
                contestsUrlSet.add(link.absUrl("href"));
            }
        } catch (IOException ex) {
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, "Failed to scrap basUrl", ex);
        }

        return contestsUrlSet;
    }

    //method to return all problem urls
    private HashSet<String> getAllProblemUrls(HashSet<String> contestsUrlSet) {
        HashSet<String> problemUrls = new HashSet<String>();
        for (String contestUrl : contestsUrlSet) {
            HashSet<String> temp = scrapeContestPage(contestUrl);
            problemUrls.addAll(temp);
        }

        return problemUrls;
    }

    //method to return problem urls from given contest page
    private HashSet<String> scrapeContestPage(String contestUrl) {
        HashSet<String> problemUrls = new HashSet<String>();
        try {
            Document doc = Jsoup.connect(contestUrl).get();
            Element pageContent = doc.getElementById("pageContent");
            Element datatable = pageContent.getElementsByClass("datatable").first();
            Element table = datatable.getElementsByTag("table").first();
            Elements rows = table.getElementsByTag("tr");
            rows.remove(0);
            for (Element row : rows) {
                mysleep();
                Element link = row.getElementsByTag("a").first();
                problemUrls.add(link.absUrl("href"));
            }
        } catch (IOException ex) {
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.SEVERE, "Failed to scrap contest page url:" + contestUrl, ex);

        }
        return problemUrls;
    }

    //method to scrap problem data of given url
    private Problem scrapeProblemData(String problemUrl) {
        Problem problem = new Problem();

        try {
            Response response = Jsoup.connect(problemUrl).execute();
            Document doc = response.parse();
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.INFO, "Processing: {0}\n"
                    + "HTTP Response status: " + response.statusCode() + " " + response.statusMessage() + "\n"
                    + "HTTP Response Size: {1} Characters", new Object[]{
                        problemUrl, doc.toString().length()
                    });

            Element problemStatement = doc.getElementsByClass("problem-statement").first();

            Element header = problemStatement.getElementsByClass("header").first();
            Node propertyTitle = header.getElementsByClass("property-title").first().nextSibling();

            StringTokenizer stringTokenizer = new StringTokenizer(propertyTitle.toString());

            problem.setPlatform(Platform.Codeforces);
            problem.setProblemUrl(problemUrl);

            Element titleElement = header.getElementsByClass("title").first();
            String title = titleElement.text();
            problem.setTitle(title);

            double timeLimit = Double.parseDouble(stringTokenizer.nextToken());
            problem.setTimeLimit(timeLimit);

            Element problemDescriptionElement = header.nextElementSibling();
            StringBuilder problemDescription = new StringBuilder();

            for (Element p : problemDescriptionElement.getElementsByTag("p")) {
                problemDescription.append(p.text());
                problemDescription.append('\n');
            }
            problem.setProblemStatement(problemDescription.toString());

            Element inputFormatElement = problemDescriptionElement.nextElementSibling();
            StringBuilder inputFormat = new StringBuilder();

            for (Element p : inputFormatElement.getElementsByTag("p")) {
                inputFormat.append(p.text());
                inputFormat.append('\n');
            }
            problem.setInputFormat(inputFormat.toString());

            Element outputFormatElement = inputFormatElement.nextElementSibling();
            StringBuilder outputFormat = new StringBuilder();

            for (Element p : outputFormatElement.getElementsByTag("p")) {
                outputFormat.append(p.text());
                outputFormat.append('\n');
            }
            problem.setOutputFormat(outputFormat.toString());

            String brTagRegex = "(?i)<br\\p{javaSpaceChar}*(?:/>|>)";
            String newline = "\n";

            Element sampleTests = outputFormatElement.nextElementSibling();
            Element sampleInputOutputElement = sampleTests.getElementsByClass("sample-test").first();

            for (Iterator<Element> iterator = sampleInputOutputElement.getElementsByTag("pre").iterator(); iterator.hasNext();) {
                Element sampleInputElement = iterator.next();
                if (!iterator.hasNext()) {
                    throw new RuntimeException("For problrm:" + problemUrl + ",Input found but no output found.");
                }
                Element sampleOutputElement = iterator.next();

                String sampleInput = sampleInputElement.html();
                sampleInput = sampleInput.replaceAll(brTagRegex, newline);
                sampleInput = sampleInput.replaceAll("\"", "");

                String sampleOutput = sampleOutputElement.html();
                sampleOutput = sampleOutput.replaceAll(brTagRegex, newline);
                sampleOutput = sampleOutput.replaceAll("\"", "");

                SampleInputOutput sampleInputOutput = new SampleInputOutput(problem, sampleInput, sampleOutput);
                problem.getSampleInputOutputs().add(sampleInputOutput);
            }

            StringBuilder explanation = new StringBuilder();
            Element note = sampleTests.nextElementSibling();

            if (note != null) {
                for (Element element : note.getElementsByTag("p")) {
                    explanation.append(element.text());
                    explanation.append('\n');
                }
                problem.setExplanation(explanation.toString());
            }

            Element sidebar = doc.getElementById("sidebar");
            Elements roundBoxes = sidebar.getElementsByClass("roundbox sidebox");
            Element thirdRoundBox = roundBoxes.get(TAG_INDEX);
            Elements tagBox = thirdRoundBox.getElementsByClass("tag-box");

            for (Element tagElement : tagBox) {
                problem.getTags().add(tagElement.text());
            }

        } catch (NumberFormatException ex) {
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.SEVERE, "Invalid time limit for problem url " + problemUrl, ex);
        } catch (RuntimeException | IOException ex) {
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.SEVERE, "Failed scrap problem data of url:" + problemUrl, ex);
        }
        return problem;
    }

    //methoda to store problem data into database
    public void storeProblem(Problem problem) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            String hql = "FROM Problem p where p.problemUrl = :problem_url";
            Problem oldProblem = (Problem) session.createQuery(hql).setString("problem_url", problem.getProblemUrl()).uniqueResult();

            String task;

            if (oldProblem != null) {
                task = "updated";
                problem.setId(oldProblem.getId());
                session.delete(oldProblem);
                session.flush();
                session.save(problem);
            } else {
                task = "saved";
                session.save(problem);
            }

            transaction.commit();
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.INFO, "{0} {1}", new Object[]{
                task, problem.getProblemUrl()
            });
        } catch (HibernateException e) {
            if (transaction != null) {
                transaction.rollback();
            }
            Logger.getLogger(CodeForcesCrawler.class.getName()).log(Level.SEVERE, "Cannot Insert/Update problem into databse: " + problem.getProblemUrl(), e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
        mysleep();
    }

    private void mysleep() {
        try {
            Thread.sleep((long) (Math.random() * 5));

        } catch (InterruptedException ex) {
            Logger.getLogger(CodeForcesCrawler.class
                    .getName()).log(Level.SEVERE, "Error in sleep function", ex);
        }
    }
}
