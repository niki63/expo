// Copyright 2018-present 650 Industries. All rights reserved.

import UIKit

public class ReloadScreenView: UIView {
  private var activityIndicator: UIActivityIndicatorView?
  private var imageView: UIImageView?
  private var currentConfiguration: ReloadScreenConfiguration?

  override init(frame: CGRect) {
    super.init(frame: frame)
    setupView()
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("Not implemented")
  }

  private func setupView() {
    autoresizingMask = [.flexibleWidth, .flexibleHeight]
    backgroundColor = UIColor.clear
  }

  func updateConfiguration(_ configuration: ReloadScreenConfiguration) {
    currentConfiguration = configuration
    backgroundColor = if configuration.imageFullScreen {
      UIColor.clear
    } else {
      configuration.backgroundColor
    }

    subviews.forEach { $0.removeFromSuperview() }

    if let imageSource = configuration.image {
      addImageView(configuration: configuration, imageSource: imageSource)
    }

    if configuration.spinner.enabled {
      addSpinner(configuration: configuration.spinner)
    }
  }

  private func addImageView(configuration: ReloadScreenConfiguration, imageSource: ReloadScreenImageSource) {
    guard let url = imageSource.url else {
      return
    }

    imageView = UIImageView()
    guard let imageView else {
      return
    }

    imageView.contentMode = configuration.imageResizeMode.contentMode
    imageView.clipsToBounds = true
    imageView.translatesAutoresizingMaskIntoConstraints = false

    addSubview(imageView)

    if configuration.imageFullScreen {
      addViewConstraints(for: imageView)
    } else if let width = imageSource.width, let height = imageSource.height, width > 0 && height > 0 {
      let scale = imageSource.scale ?? 1.0
      let scaledWidth = width * scale
      let scaledHeight = height * scale

      NSLayoutConstraint.activate([
        imageView.centerXAnchor.constraint(equalTo: centerXAnchor),
        imageView.centerYAnchor.constraint(equalTo: centerYAnchor),
        imageView.widthAnchor.constraint(equalToConstant: scaledWidth),
        imageView.heightAnchor.constraint(equalToConstant: scaledHeight)
      ])
    } else {
      addViewConstraints(for: imageView)
    }

    loadImage(source: url, into: imageView)
  }

  private func addViewConstraints(for imageView: UIImageView) {
    NSLayoutConstraint.activate([
      imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
      imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
      imageView.topAnchor.constraint(equalTo: topAnchor),
      imageView.bottomAnchor.constraint(equalTo: bottomAnchor)
    ])
  }

  private func addSpinner(configuration: SpinnerConfiguration) {
    activityIndicator = UIActivityIndicatorView()
    guard let activityIndicator = activityIndicator else {
      return
    }
    activityIndicator.style = switch configuration.size {
    case .large:
      .large
    case .medium:
      .medium
    case .small:
      .medium
    }

    activityIndicator.color = configuration.color
    activityIndicator.translatesAutoresizingMaskIntoConstraints = false

    addSubview(activityIndicator)

    NSLayoutConstraint.activate([
      activityIndicator.centerXAnchor.constraint(equalTo: centerXAnchor),
      activityIndicator.centerYAnchor.constraint(equalTo: centerYAnchor),
      activityIndicator.widthAnchor.constraint(equalToConstant: configuration.size.spinnerSize),
      activityIndicator.heightAnchor.constraint(equalToConstant: configuration.size.spinnerSize)
    ])

    activityIndicator.startAnimating()
  }

  private func loadImage(source: URL, into imageView: UIImageView) {
    Task {
      if source.scheme == "http" || source.scheme == "https" {
        await loadImageFromURL(source, into: imageView)
      } else if source.scheme == "data" {
        await loadImageFromDataURL(source, into: imageView)
      } else if source.scheme == "file" {
        await loadImageFromFile(source, into: imageView)
      } else {
        await loadImageFromBundle(source.absoluteString, into: imageView)
      }
    }
  }

  private func loadImageFromURL(_ url: URL, into imageView: UIImageView) async {
    do {
      let (data, _) = try await URLSession.shared.data(from: url)
      guard let image = UIImage(data: data) else {
        await MainActor.run {
          self.handleImageLoadFailure()
        }
        return
      }

      await MainActor.run {
        imageView.image = image
      }
    } catch {
      await MainActor.run {
        self.handleImageLoadFailure()
      }
    }
  }

  private func loadImageFromDataURL(_ dataURL: URL, into imageView: UIImageView) async {
    do {
      let data = try Data(contentsOf: dataURL)
      guard let image = UIImage(data: data) else {
        await MainActor.run {
          self.handleImageLoadFailure()
        }
        return
      }

      await MainActor.run {
        imageView.image = image
      }
    } catch {
      await MainActor.run {
        self.handleImageLoadFailure()
      }
    }
  }

  private func loadImageFromFile(_ fileURL: URL, into imageView: UIImageView) async {
    guard let image = UIImage(contentsOfFile: fileURL.path) else {
      await MainActor.run {
        self.handleImageLoadFailure()
      }
      return
    }

    await MainActor.run {
      imageView.image = image
    }
  }

  private func loadImageFromBundle(_ imageName: String, into imageView: UIImageView) async {
    var cleanName = imageName

    if let dotIndex = cleanName.lastIndex(of: ".") {
      cleanName = String(cleanName[..<dotIndex])
    }

    guard let image = UIImage(named: cleanName) else {
      await MainActor.run {
        self.handleImageLoadFailure()
      }
      return
    }

    await MainActor.run {
      imageView.image = image
    }
  }

  private func handleImageLoadFailure() {
    imageView?.isHidden = true

    if let config = currentConfiguration {
      backgroundColor = config.backgroundColor
      let spinnerConfig = SpinnerConfiguration(
        enabled: true,
        color: config.spinner.color,
        size: config.spinner.size
      )
      addSpinner(configuration: spinnerConfig)
    }
  }
}
