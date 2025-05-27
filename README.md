# PDF-Processor

A Java-based application for processing PDF documents using OCR (Optical Character Recognition) capabilities through Tesseract.

## Description

PDF-Processor is an academic project that demonstrates the use of Tesseract OCR technology to process and extract text from PDF documents. The application is built with Java and uses Tess4j, a Java wrapper for Tesseract OCR.

## Prerequisites

- Java 8 or higher
- Maven
- Tesseract OCR (included in tessdata folder)

## Setup

1. Clone the repository:
```bash
git clone https://github.com/stramosk98/PDF-Processor.git
```

2. Navigate to the project directory:
```bash
cd PDF-Processor
```

3. Build the project using Maven:
```bash
mvn clean install
```

## Project Structure

- `src/` - Source code files
- `tessdata/` - Tesseract OCR data files
- `target/` - Compiled files and build output
- `pom.xml` - Maven project configuration

## Dependencies

- Tess4j (v5.8.0) - Java wrapper for Tesseract OCR

## Building

The project uses Maven for dependency management and building. To create an executable JAR:

```bash
mvn package
```

This will create a JAR file in the `target` directory.

## Main Features

- PDF document processing
- OCR text extraction using Tesseract
- Java-based server implementation

## Contributing

This is an academic project. Please check with the project maintainers before making any contributions.

## License

This project is created for academic purposes. Please consult with the project maintainers regarding usage and distribution. 
